/* ============================================
   Forever Inclusive Academic Tutoring
   Booking JS - js/booking.js
   Child form, price calculator, ratings
   ============================================ */

(function () {
  'use strict';

  const BOOKING_KEY = 'fiat_booking';
  const REVIEWS_KEY = 'fiat_reviews';
  const CHILD_TYPE_KEY = 'childType';

  // Area rate mapping
  const AREA_RATES = {
    120: ['Al Wakra', 'Al Wukair', 'Ezdan'],
    150: ['West Bay', 'The Pearl', 'Lusail', 'Al Sadd', 'Musheireb', 'Al Waab', 'Old Airport', 'Bin Mahmoud', 'Al Hilal', 'Doha other'],
    200: ['Al Khor', 'Al Shamal', 'Al Ruwais', 'Other / Outside'],
  };

  // ---------- Child type selection (choose.html) ----------
  function initChoosePage() {
    const cards = document.querySelectorAll('.choose-card');
    if (!cards.length) return;

    cards.forEach((card) => {
      card.addEventListener('click', () => {
        const type = card.dataset.type; // 'standard' or 'autism'
        localStorage.setItem(CHILD_TYPE_KEY, type);

        const user = window.FIATAuth && window.FIATAuth.getCurrentUser();
        if (user) {
          window.location.href = 'child-info.html';
        } else {
          window.location.href = 'signup.html';
        }
      });

      // Keyboard support
      card.setAttribute('tabindex', '0');
      card.setAttribute('role', 'button');
      card.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          card.click();
        }
      });
    });
  }

  // ---------- Price calculator ----------
  function getRateForArea(areaValue) {
    for (const [rate, areas] of Object.entries(AREA_RATES)) {
      if (areas.includes(areaValue)) return parseInt(rate, 10);
    }
    return 150; // default
  }

  function updatePriceDisplay() {
    const select = document.getElementById('child-area');
    const display = document.getElementById('price-rate');
    if (!select || !display) return;

    const rate = getRateForArea(select.value);
    display.textContent = rate;
  }

  // ---------- Child info form ----------
  function initChildForm() {
    const form = document.getElementById('child-form');
    if (!form) return;

    // Protect page
    if (window.FIATAuth && !window.FIATAuth.protectPage()) return;

    // Show autism fields if needed
    const childType = localStorage.getItem(CHILD_TYPE_KEY) || 'standard';
    const autismSection = document.getElementById('autism-fields');
    if (autismSection) {
      autismSection.style.display = childType === 'autism' ? 'block' : 'none';
    }

    // Update type badge
    const typeBadge = document.getElementById('child-type-badge');
    if (typeBadge) {
      typeBadge.textContent =
        childType === 'autism' ? 'Autism-Friendly Learning' : 'Standard Learning';
    }

    // Price calculator
    const areaSelect = document.getElementById('child-area');
    if (areaSelect) {
      areaSelect.addEventListener('change', updatePriceDisplay);
      updatePriceDisplay();
    }

    // Form submit
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      saveBooking(form);
    });
  }

  function saveBooking(form) {
    const childType = localStorage.getItem(CHILD_TYPE_KEY) || 'standard';
    const subjects = Array.from(form.querySelectorAll('input[name="subjects"]:checked')).map(
      (cb) => cb.value
    );

    const booking = {
      childType,
      name: form.querySelector('#child-name').value.trim(),
      age: form.querySelector('#child-age').value,
      year: form.querySelector('#child-year').value,
      curriculum: form.querySelector('#child-curriculum').value,
      subjects,
      area: form.querySelector('#child-area').value,
      rate: getRateForArea(form.querySelector('#child-area').value),
      address: form.querySelector('#child-address').value.trim(),
      preferredTimes: form.querySelector('#child-times').value.trim(),
      communication: form.querySelector('#child-communication')?.value.trim() || '',
      sensory: form.querySelector('#child-sensory')?.value.trim() || '',
      interests: form.querySelector('#child-interests')?.value.trim() || '',
      notes: form.querySelector('#child-notes')?.value.trim() || '',
      createdAt: new Date().toISOString(),
      parent: window.FIATAuth ? window.FIATAuth.getCurrentUser() : null,
    };

    // Basic validation
    if (!booking.name || !booking.area || subjects.length === 0) {
      alert('Please fill in the required fields (name, area, and at least one subject).');
      return;
    }

    localStorage.setItem(BOOKING_KEY, JSON.stringify(booking));
    showSuccessModal(booking);
  }

  // ---------- Success modal ----------
  function showSuccessModal(booking) {
    const overlay = document.getElementById('success-modal');
    if (!overlay) return;

    // Build WhatsApp message
    const msg = buildWhatsAppMessage(booking);
    const waBtn = document.getElementById('wa-confirm-btn');
    if (waBtn) {
      waBtn.href = `https://wa.me/97459978518?text=${encodeURIComponent(msg)}`;
    }

    overlay.classList.add('active');
    document.body.style.overflow = 'hidden';
  }

  function buildWhatsAppMessage(b) {
    let text = `Hello! I would like to book a tutoring session.\n\n`;
    text += `*Child:* ${b.name}\n`;
    text += `*Age / Year:* ${b.age} / ${b.year}\n`;
    text += `*Learning path:* ${b.childType === 'autism' ? 'Autism-Friendly' : 'Standard'}\n`;
    text += `*Curriculum:* ${b.curriculum}\n`;
    text += `*Subjects:* ${b.subjects.join(', ')}\n`;
    text += `*Area:* ${b.area} (${b.rate} QAR/hr)\n`;
    text += `*Address / Landmark:* ${b.address || '—'}\n`;
    text += `*Preferred times:* ${b.preferredTimes || '—'}\n`;
    if (b.childType === 'autism') {
      if (b.communication) text += `*Communication style:* ${b.communication}\n`;
      if (b.sensory) text += `*Sensory notes:* ${b.sensory}\n`;
      if (b.interests) text += `*Interests / triggers:* ${b.interests}\n`;
      if (b.notes) text += `*Additional notes:* ${b.notes}\n`;
    }
    text += `\nLooking forward to hearing from you!`;
    return text;
  }

  // Close modal
  function initModalClose() {
    const overlay = document.getElementById('success-modal');
    if (!overlay) return;

    const closeBtn = overlay.querySelector('.modal-close');
    if (closeBtn) {
      closeBtn.addEventListener('click', () => {
        overlay.classList.remove('active');
        document.body.style.overflow = '';
      });
    }

    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) {
        overlay.classList.remove('active');
        document.body.style.overflow = '';
      }
    });
  }

  // ---------- Dashboard ----------
  function initDashboard() {
    const summary = document.getElementById('booking-summary');
    if (!summary) return;

    // Protect
    if (window.FIATAuth && !window.FIATAuth.protectPage()) return;

    const user = window.FIATAuth.getCurrentUser();
    const welcome = document.getElementById('welcome-msg');
    if (welcome && user) {
      welcome.textContent = `Welcome, ${user.name}!`;
    }

    // Booking summary
    let booking;
    try {
      booking = JSON.parse(localStorage.getItem(BOOKING_KEY));
    } catch {
      booking = null;
    }

    const content = document.getElementById('summary-content');
    if (content) {
      if (booking) {
        content.innerHTML = `
          <dl>
            <dt>Child</dt><dd>${escapeHtml(booking.name)}</dd>
            <dt>Age / Year</dt><dd>${escapeHtml(booking.age)} / ${escapeHtml(booking.year)}</dd>
            <dt>Learning path</dt><dd>${booking.childType === 'autism' ? 'Autism-Friendly Learning' : 'Standard Learning'}</dd>
            <dt>Curriculum</dt><dd>${escapeHtml(booking.curriculum)}</dd>
            <dt>Subjects</dt><dd>${escapeHtml(booking.subjects.join(', '))}</dd>
            <dt>Area</dt><dd>${escapeHtml(booking.area)}</dd>
            <dt>Rate</dt><dd>${booking.rate} QAR/hr</dd>
            <dt>Address</dt><dd>${escapeHtml(booking.address || '—')}</dd>
            <dt>Preferred times</dt><dd>${escapeHtml(booking.preferredTimes || '—')}</dd>
          </dl>
        `;
      } else {
        content.innerHTML = `<p>No booking yet. <a href="choose.html">Start a new booking</a>.</p>`;
      }
    }

    // Reviews
    initReviews();
  }

  function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  // ---------- Reviews ----------
  // Sample reviews – clearly marked for replacement with real ones later
  const SAMPLE_REVIEWS = [
    {
      id: 'sample1',
      name: 'Sara M.',
      rating: 5,
      comment: 'Wonderful tutor! My daughter looks forward to every session. Patient, kind and very professional.',
      date: '2025-11-12',
      sample: true,
    },
    {
      id: 'sample2',
      name: 'Ahmed K.',
      rating: 5,
      comment: 'Excellent support for our son. The autism-friendly approach made a real difference. Highly recommend.',
      date: '2025-12-03',
      sample: true,
    },
    {
      id: 'sample3',
      name: 'Fatima R.',
      rating: 5,
      comment: 'Flexible schedule and comes to our home in Al Wakra. Maths grades improved within weeks!',
      date: '2026-01-18',
      sample: true,
    },
    {
      id: 'sample4',
      name: 'James L.',
      rating: 5,
      comment: 'IGCSE prep was spot on. Clear explanations and great exam strategies. Thank you!',
      date: '2026-02-09',
      sample: true,
    },
  ];

  function getReviews() {
    try {
      const stored = JSON.parse(localStorage.getItem(REVIEWS_KEY));
      if (stored && stored.length) return stored;
    } catch {}
    // Seed samples
    localStorage.setItem(REVIEWS_KEY, JSON.stringify(SAMPLE_REVIEWS));
    return SAMPLE_REVIEWS;
  }

  function saveReviews(reviews) {
    localStorage.setItem(REVIEWS_KEY, JSON.stringify(reviews));
  }

  function initReviews() {
    renderReviews();
    initReviewForm();
  }

  function renderReviews() {
    const list = document.getElementById('reviews-list');
    const avgEl = document.getElementById('avg-rating');
    const countEl = document.getElementById('review-count');
    if (!list) return;

    const reviews = getReviews();
    const avg =
      reviews.length > 0
        ? (reviews.reduce((s, r) => s + r.rating, 0) / reviews.length).toFixed(1)
        : '5.0';

    if (avgEl) avgEl.textContent = avg;
    if (countEl) countEl.textContent = reviews.length;

    // Fill summary stars
    const summaryStars = document.querySelectorAll('#summary-stars .star');
    const full = Math.round(parseFloat(avg));
    summaryStars.forEach((s, i) => {
      if (i < full) s.classList.add('filled');
      else s.classList.remove('filled');
    });

    list.innerHTML = reviews
      .map(
        (r) => `
      <article class="review-card reveal">
        <div class="reviewer">${escapeHtml(r.name)}${r.sample ? ' <small style="opacity:0.6">(sample)</small>' : ''}</div>
        <div class="review-stars">${'★'.repeat(r.rating)}${'☆'.repeat(5 - r.rating)}</div>
        <p>${escapeHtml(r.comment)}</p>
      </article>
    `
      )
      .join('');

    // Re-trigger reveal if needed
    if (window.FIAT && window.FIAT.$) {
      // Simple: add visible after short delay
      setTimeout(() => {
        list.querySelectorAll('.reveal').forEach((el) => el.classList.add('visible'));
      }, 100);
    }
  }

  function initReviewForm() {
    const form = document.getElementById('review-form');
    if (!form) return;

    let selectedRating = 0;
    const stars = form.querySelectorAll('.star-input button');

    stars.forEach((btn, i) => {
      btn.addEventListener('click', () => {
        selectedRating = i + 1;
        stars.forEach((s, j) => {
          s.classList.toggle('active', j < selectedRating);
        });
      });
    });

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const comment = form.querySelector('#review-comment').value.trim();
      if (selectedRating < 1) {
        alert('Please select a star rating.');
        return;
      }
      if (!comment) {
        alert('Please write a short comment.');
        return;
      }

      const user = window.FIATAuth ? window.FIATAuth.getCurrentUser() : null;
      const name = user ? user.name.split(' ')[0] : 'Parent';

      const reviews = getReviews();
      reviews.unshift({
        id: 'user-' + Date.now(),
        name,
        rating: selectedRating,
        comment,
        date: new Date().toISOString().slice(0, 10),
        sample: false,
      });
      saveReviews(reviews);
      renderReviews();

      form.reset();
      selectedRating = 0;
      stars.forEach((s) => s.classList.remove('active'));
    });
  }

  // ---------- Init ----------
  document.addEventListener('DOMContentLoaded', () => {
    initChoosePage();
    initChildForm();
    initModalClose();
    initDashboard();
  });
})();
