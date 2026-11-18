// Placing an order (ORD-1 – ORD-4): a saved address, a payment method and the cart as the server
// sees it now. If the cart changed on the way, the server says what changed and nothing is ordered.

import { api } from '../api.js';
import { $, fill, formValues, h } from '../dom.js';
import { formatMoney, t } from '../i18n.js';
import { busy, refreshCartBadge, showError, showMessage, startPage } from '../layout.js';
import { showNotices, totals } from '../cart-view.js';

await startPage({ signInRequired: true });
const form = $('#checkout');

await Promise.all([showAddresses(), showSummary()]);

form.addEventListener('submit', (event) => {
  event.preventDefault();
  busy(form, async () => {
    const values = formValues(form);
    if (!values.addressId) {
      showMessage(t('checkout.chooseAddress'), 'error');
      return;
    }
    try {
      const order = await api.post('/orders', { addressId: Number(values.addressId), paymentMethod: values.paymentMethod });
      location.href = `orders.html?number=${encodeURIComponent(order.orderNumber)}&placed=1`;
    } catch (error) {
      await showSummary();
      throw error;
    }
  }, form);
});

async function showAddresses() {
  try {
    const profile = await api.get('/me');
    const box = $('#addresses');
    if (profile.addresses.length === 0) {
      fill(box, h('p', { class: 'hint' }, t('checkout.noAddress')));
      return;
    }
    fill(box, profile.addresses.map((saved) => h('label', { class: 'choice' },
      h('input', { type: 'radio', name: 'addressId', value: saved.id, checked: saved.isDefault }),
      h('span', {}, h('strong', {}, saved.label), h('br'), saved.address.recipient, ', ', saved.address.line, ', ',
        saved.address.district, ' / ', saved.address.city, ' ', saved.address.postalCode))));
  } catch (error) {
    showError(error);
  }
}

async function showSummary() {
  try {
    const cart = await api.get('/cart');
    refreshCartBadge(cart);
    showNotices($('#notices'), cart.notices);
    const box = $('#summary');
    if (cart.lines.length === 0) {
      fill(box, h('p', { class: 'empty' }, t('cart.empty'), ' ', h('a', { href: 'index.html' }, t('cart.browse'))));
      form.querySelector('button[type="submit"]').disabled = true;
      return;
    }
    fill(box,
      h('ul', { class: 'hint' }, cart.lines.map((line) =>
        h('li', {}, `${line.quantity} × ${line.title} — ${formatMoney(line.lineTotal)}`))),
      totals(cart));
  } catch (error) {
    showError(error);
  }
}
