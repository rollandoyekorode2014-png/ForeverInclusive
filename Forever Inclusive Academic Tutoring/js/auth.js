/* ============================================
   Forever Inclusive Academic Tutoring
   Auth JS - js/auth.js
   Sign up, login, logout, route protection
   NOTE: This is a DEMO using localStorage only.
   A real backend is required for production.
   ============================================ */

(function () {
  'use strict';

  const USERS_KEY = 'fiat_users';
  const CURRENT_KEY = 'currentUser';

  // ---------- Storage helpers ----------
  function getUsers() {
    try {
      return JSON.parse(localStorage.getItem(USERS_KEY)) || [];
    } catch {
      return [];
    }
  }

  function saveUsers(users) {
    localStorage.setItem(USERS_KEY, JSON.stringify(users));
  }

  function getCurrentUser() {
    try {
      return JSON.parse(localStorage.getItem(CURRENT_KEY));
    } catch {
      return null;
    }
  }

  function setCurrentUser(user) {
    if (user) {
      // Don't store password in current session
      const { password, ...safe } = user;
      localStorage.setItem(CURRENT_KEY, JSON.stringify(safe));
    } else {
      localStorage.removeItem(CURRENT_KEY);
    }
  }

  // ---------- Validation ----------
  function validateEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  }

  function validatePhone(phone) {
    // Accept +974 followed by 8 digits, or just 8 digits
    const cleaned = phone.replace(/\s+/g, '');
    return /^(\+974)?[3-7]\d{7}$/.test(cleaned);
  }

  function showError(group, msg) {
    group.classList.add('has-error');
    const err = group.querySelector('.error');
    if (err) err.textContent = msg;
  }

  function clearError(group) {
    group.classList.remove('has-error');
  }

  // ---------- Sign Up ----------
  function handleSignUp(form) {
    const name = form.querySelector('#signup-name').value.trim();
    const email = form.querySelector('#signup-email').value.trim().toLowerCase();
    const phone = form.querySelector('#signup-phone').value.trim();
    const password = form.querySelector('#signup-password').value;
    const confirm = form.querySelector('#signup-confirm').value;

    let valid = true;

    // Clear previous
    form.querySelectorAll('.form-group').forEach(clearError);

    if (!name || name.length < 2) {
      showError(form.querySelector('#signup-name').closest('.form-group'), 'Please enter your full name.');
      valid = false;
    }

    if (!validateEmail(email)) {
      showError(form.querySelector('#signup-email').closest('.form-group'), 'Please enter a valid email address.');
      valid = false;
    }

    if (!validatePhone(phone)) {
      showError(form.querySelector('#signup-phone').closest('.form-group'), 'Please enter a valid Qatar phone (+974 XXXX XXXX).');
      valid = false;
    }

    if (password.length < 6) {
      showError(form.querySelector('#signup-password').closest('.form-group'), 'Password must be at least 6 characters.');
      valid = false;
    }

    if (password !== confirm) {
      showError(form.querySelector('#signup-confirm').closest('.form-group'), 'Passwords do not match.');
      valid = false;
    }

    if (!valid) return;

    const users = getUsers();
    if (users.some((u) => u.email === email)) {
      showError(form.querySelector('#signup-email').closest('.form-group'), 'This email is already registered. Please log in.');
      return;
    }

    const newUser = {
      id: Date.now().toString(36),
      name,
      email,
      phone: phone.startsWith('+') ? phone : '+974' + phone.replace(/\D/g, '').slice(-8),
      password, // DEMO ONLY – never store plain passwords in production
      createdAt: new Date().toISOString(),
    };

    users.push(newUser);
    saveUsers(users);
    setCurrentUser(newUser);

    // Continue to child-info
    window.location.href = 'child-info.html';
  }

  // ---------- Log In ----------
  function handleLogin(form) {
    const email = form.querySelector('#login-email').value.trim().toLowerCase();
    const password = form.querySelector('#login-password').value;

    form.querySelectorAll('.form-group').forEach(clearError);

    if (!validateEmail(email)) {
      showError(form.querySelector('#login-email').closest('.form-group'), 'Please enter a valid email.');
      return;
    }

    if (!password) {
      showError(form.querySelector('#login-password').closest('.form-group'), 'Please enter your password.');
      return;
    }

    const users = getUsers();
    const user = users.find((u) => u.email === email && u.password === password);

    if (!user) {
      showError(form.querySelector('#login-password').closest('.form-group'), 'Invalid email or password.');
      return;
    }

    setCurrentUser(user);
    window.location.href = 'child-info.html';
  }

  // ---------- Password toggle ----------
  function initPasswordToggles() {
    document.querySelectorAll('.toggle-pass').forEach((btn) => {
      btn.addEventListener('click', () => {
        const input = btn.closest('.password-wrap').querySelector('input');
        if (input.type === 'password') {
          input.type = 'text';
          btn.textContent = 'Hide';
          btn.setAttribute('aria-label', 'Hide password');
        } else {
          input.type = 'password';
          btn.textContent = 'Show';
          btn.setAttribute('aria-label', 'Show password');
        }
      });
    });
  }

  // ---------- Tabs ----------
  function initTabs() {
    const tabBtns = document.querySelectorAll('.tab-btn');
    const panels = document.querySelectorAll('.tab-panel');

    tabBtns.forEach((btn) => {
      btn.addEventListener('click', () => {
        const target = btn.dataset.tab;
        tabBtns.forEach((b) => b.classList.remove('active'));
        panels.forEach((p) => p.classList.remove('active'));
        btn.classList.add('active');
        const panel = document.getElementById(target);
        if (panel) panel.classList.add('active');
      });
    });
  }

  // ---------- Route protection ----------
  function protectPage() {
    const user = getCurrentUser();
    if (!user) {
      // Remember where they were trying to go
      sessionStorage.setItem('redirectAfterAuth', window.location.pathname.split('/').pop());
      window.location.href = 'signup.html';
      return false;
    }
    return true;
  }

  // ---------- Init ----------
  document.addEventListener('DOMContentLoaded', () => {
    // Signup form
    const signupForm = document.getElementById('signup-form');
    if (signupForm) {
      signupForm.addEventListener('submit', (e) => {
        e.preventDefault();
        handleSignUp(signupForm);
      });
    }

    // Login form
    const loginForm = document.getElementById('login-form');
    if (loginForm) {
      loginForm.addEventListener('submit', (e) => {
        e.preventDefault();
        handleLogin(loginForm);
      });
    }

    initPasswordToggles();
    initTabs();

    // If already logged in and on signup page, go to child-info or dashboard
    if (document.getElementById('signup-form') && getCurrentUser()) {
      const childType = localStorage.getItem('childType');
      if (childType) {
        window.location.href = 'child-info.html';
      }
    }
  });

  // Export for other scripts
  window.FIATAuth = {
    getCurrentUser,
    setCurrentUser,
    protectPage,
    getUsers,
  };
})();
