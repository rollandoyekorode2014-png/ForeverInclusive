/* ============================================
   Forever Inclusive Academic Tutoring
   Main JS - js/main.js
   Navbar, scroll reveal, counters, helpers
   ============================================ */

(function () {
  'use strict';

  // ---------- Helpers ----------
  function $(sel, ctx = document) {
    return ctx.querySelector(sel);
  }

  function $$(sel, ctx = document) {
    return Array.from(ctx.querySelectorAll(sel));
  }

  // ---------- Navbar / Hamburger ----------
  function initNavbar() {
    const hamburger = $('.hamburger');
    const mobileMenu = $('.mobile-menu');
    const navLinks = $$('.nav-links a, .mobile-menu a');

    if (hamburger && mobileMenu) {
      hamburger.addEventListener('click', () => {
        hamburger.classList.toggle('active');
        mobileMenu.classList.toggle('active');
        document.body.style.overflow = mobileMenu.classList.contains('active') ? 'hidden' : '';
      });

      // Close on link click
      $$('.mobile-menu a').forEach((link) => {
        link.addEventListener('click', () => {
          hamburger.classList.remove('active');
          mobileMenu.classList.remove('active');
          document.body.style.overflow = '';
        });
      });
    }

    // Active link highlighting
    const path = window.location.pathname.split('/').pop() || 'index.html';
    navLinks.forEach((a) => {
      const href = a.getAttribute('href');
      if (href === path || (path === '' && href === 'index.html')) {
        a.classList.add('active');
      }
    });
  }

  // ---------- Auth-aware navbar ----------
  function updateAuthUI() {
    const isLoggedIn = !!localStorage.getItem('currentUser');
    const authBtn = $('#nav-auth-btn');
    const dashBtn = $('#nav-dash-btn');
    const logoutBtn = $('#nav-logout-btn');
    const mobileAuth = $('#mobile-auth');
    const mobileDash = $('#mobile-dash');
    const mobileLogout = $('#mobile-logout');

    if (authBtn) authBtn.style.display = isLoggedIn ? 'none' : '';
    if (dashBtn) dashBtn.style.display = isLoggedIn ? '' : 'none';
    if (logoutBtn) logoutBtn.style.display = isLoggedIn ? '' : 'none';

    if (mobileAuth) mobileAuth.style.display = isLoggedIn ? 'none' : '';
    if (mobileDash) mobileDash.style.display = isLoggedIn ? '' : 'none';
    if (mobileLogout) mobileLogout.style.display = isLoggedIn ? '' : 'none';

    if (logoutBtn) {
      logoutBtn.addEventListener('click', (e) => {
        e.preventDefault();
        localStorage.removeItem('currentUser');
        window.location.href = 'index.html';
      });
    }
    if (mobileLogout) {
      mobileLogout.addEventListener('click', (e) => {
        e.preventDefault();
        localStorage.removeItem('currentUser');
        window.location.href = 'index.html';
      });
    }
  }

  // ---------- Scroll Reveal ----------
  function initScrollReveal() {
    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) {
      $$('.reveal').forEach((el) => el.classList.add('visible'));
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('visible');
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: '0px 0px -40px 0px' }
    );

    $$('.reveal').forEach((el) => observer.observe(el));
  }

  // ---------- Animated Counters ----------
  function initCounters() {
    const counters = $$('.counter-item .num');
    if (!counters.length) return;

    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          const el = entry.target;
          const target = el.dataset.target || el.textContent;
          // Simple display – for "Y1–Y12", "3", "100%" we just ensure visible
          if (prefersReduced) {
            el.textContent = target;
          } else {
            el.style.opacity = '0';
            setTimeout(() => {
              el.style.transition = 'opacity 0.5s';
              el.style.opacity = '1';
              el.textContent = target;
            }, 100);
          }
          observer.unobserve(el);
        });
      },
      { threshold: 0.5 }
    );

    counters.forEach((c) => observer.observe(c));
  }

  // ---------- Star fill on scroll ----------
  function initStarFill() {
    const starGroups = $$('.stars[data-animate]');
    if (!starGroups.length) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          const stars = $$('.star', entry.target);
          stars.forEach((star, i) => {
            setTimeout(() => {
              star.classList.add('filled', 'animate-fill');
            }, i * 120);
          });
          observer.unobserve(entry.target);
        });
      },
      { threshold: 0.4 }
    );

    starGroups.forEach((g) => observer.observe(g));
  }

  // ---------- Button ripple ----------
  function initRipples() {
    $$('.btn').forEach((btn) => {
      btn.addEventListener('click', function (e) {
        const rect = this.getBoundingClientRect();
        const ripple = document.createElement('span');
        ripple.classList.add('ripple');
        const size = Math.max(rect.width, rect.height);
        ripple.style.width = ripple.style.height = size + 'px';
        ripple.style.left = e.clientX - rect.left - size / 2 + 'px';
        ripple.style.top = e.clientY - rect.top - size / 2 + 'px';
        this.appendChild(ripple);
        setTimeout(() => ripple.remove(), 600);
      });
    });
  }

  // ---------- Language toggle (basic) ----------
  function initLangToggle() {
    const toggles = $$('.lang-toggle button');
    if (!toggles.length) return;

    const current = localStorage.getItem('lang') || 'en';
    toggles.forEach((btn) => {
      if (btn.dataset.lang === current) btn.classList.add('active');
      btn.addEventListener('click', () => {
        localStorage.setItem('lang', btn.dataset.lang);
        toggles.forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
        // Simple demo: could expand translations later
        document.documentElement.lang = btn.dataset.lang;
      });
    });
  }

  // ---------- Smooth scroll for anchor links ----------
  function initSmoothScroll() {
    $$('a[href^="#"]').forEach((a) => {
      a.addEventListener('click', (e) => {
        const id = a.getAttribute('href');
        if (id === '#') return;
        const target = $(id);
        if (target) {
          e.preventDefault();
          target.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      });
    });
  }

  // ---------- Init on DOM ready ----------
  document.addEventListener('DOMContentLoaded', () => {
    initNavbar();
    updateAuthUI();
    initScrollReveal();
    initCounters();
    initStarFill();
    initRipples();
    initLangToggle();
    initSmoothScroll();

    // Hero animation class
    const heroText = $('.hero-text');
    if (heroText) heroText.classList.add('animate');
  });

  // Expose helpers if needed
  window.FIAT = { $, $$, updateAuthUI };
})();
