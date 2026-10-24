/* ========== script.js ========== */
/* Track selection, localStorage accounts, form validation,
   scroll reveals, counters, mobile nav. No external libs. */

(function () {
  "use strict";

  /* ---------- Helpers ---------- */
  const $ = (sel, ctx = document) => ctx.querySelector(sel);
  const $$ = (sel, ctx = document) => [...ctx.querySelectorAll(sel)];

  const STORAGE_TRACK = "htq_track";
  const STORAGE_USER = "htq_user";
  const STORAGE_ACCOUNTS = "htq_accounts";

  function getTrack() {
    return localStorage.getItem(STORAGE_TRACK) || null;
  }
  function setTrack(t) {
    localStorage.setItem(STORAGE_TRACK, t);
  }
  function getUser() {
    try {
      return JSON.parse(localStorage.getItem(STORAGE_USER));
    } catch {
      return null;
    }
  }
  function setUser(u) {
    localStorage.setItem(STORAGE_USER, JSON.stringify(u));
  }
  function clearUser() {
    localStorage.removeItem(STORAGE_USER);
  }
  function getAccounts() {
    try {
      return JSON.parse(localStorage.getItem(STORAGE_ACCOUNTS)) || {};
    } catch {
      return {};
    }
  }
  function saveAccount(email, data) {
    const accounts = getAccounts();
    accounts[email.toLowerCase()] = data;
    localStorage.setItem(STORAGE_ACCOUNTS, JSON.stringify(accounts));
  }

  /* ---------- Mobile nav ---------- */
  const toggle = $(".nav-toggle");
  if (toggle) {
    toggle.addEventListener("click", () => {
      const open = document.body.classList.toggle("nav-open");
      toggle.setAttribute("aria-expanded", open);
    });
  }

  /* ---------- INDEX PAGE ---------- */
  if ($(".track-section")) {
    // Welcome banner
    const track = getTrack();
    const banner = $("#welcome-banner");
    if (track && banner) {
      banner.classList.remove("hidden");
      $("#saved-track-label").textContent =
        track === "sen" ? "SEN / Special Needs" : "Standard Learner";
      $("#change-track-btn").addEventListener("click", () => {
        localStorage.removeItem(STORAGE_TRACK);
        banner.classList.add("hidden");
      });
    }

    // Track card selection
    $$(".track-card").forEach((card) => {
      card.addEventListener("click", () => {
        const chosen = card.dataset.track;
        card.classList.add("selecting");
        setTrack(chosen);
        setTimeout(() => {
          window.location.href = "signup.html";
        }, 380);
      });
    });
  }

  /* ---------- SIGNUP PAGE ---------- */
  if ($("#signup-form")) {
    // Pre-fill track from localStorage
    const trackSelect = $("#track");
    const saved = getTrack();
    if (saved) trackSelect.value = saved;

    // Toggle SEN fields
    function updateSenFields() {
      const isSen = trackSelect.value === "sen";
      $("#sen-fields").classList.toggle("hidden", !isSen);
    }
    trackSelect.addEventListener("change", updateSenFields);
    updateSenFields();

    // Auth tab toggle
    const btnSignup = $("#btn-signup");
    const btnLogin = $("#btn-login");
    const formSignup = $("#signup-form");
    const formLogin = $("#login-form");

    btnSignup.addEventListener("click", () => {
      btnSignup.classList.add("active");
      btnSignup.setAttribute("aria-selected", "true");
      btnLogin.classList.remove("active");
      btnLogin.setAttribute("aria-selected", "false");
      formSignup.classList.remove("hidden");
      formLogin.classList.add("hidden");
    });
    btnLogin.addEventListener("click", () => {
      btnLogin.classList.add("active");
      btnLogin.setAttribute("aria-selected", "true");
      btnSignup.classList.remove("active");
      btnSignup.setAttribute("aria-selected", "false");
      formLogin.classList.remove("hidden");
      formSignup.classList.add("hidden");
    });

    // Real-time validation helpers
    function showError(input, msg) {
      const group = input.closest(".form-group");
      group.classList.add("invalid");
      group.querySelector(".error-msg").textContent = msg;
      input.classList.add("shake");
      setTimeout(() => input.classList.remove("shake"), 450);
    }
    function clearError(input) {
      const group = input.closest(".form-group");
      group.classList.remove("invalid");
      group.querySelector(".error-msg").textContent = "";
    }

    function validateField(input) {
      const val = input.value.trim();
      if (input.required && !val) {
        showError(input, "This field is required");
        return false;
      }
      if (input.type === "email" && val) {
        const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!re.test(val)) {
          showError(input, "Please enter a valid email");
          return false;
        }
      }
      if (input.type === "tel" && val) {
        const digits = val.replace(/\D/g, "");
        if (digits.length < 8) {
          showError(input, "Please enter a valid phone number");
          return false;
        }
      }
      if (input.id === "password" && val.length < 6) {
        showError(input, "Password must be at least 6 characters");
        return false;
      }
      clearError(input);
      return true;
    }

    // Live validation on blur / input
    $$("#signup-form input, #signup-form select").forEach((el) => {
      el.addEventListener("blur", () => validateField(el));
      el.addEventListener("input", () => {
        if (el.closest(".form-group").classList.contains("invalid")) {
          validateField(el);
        }
      });
    });

    // Sign-up submit
    formSignup.addEventListener("submit", (e) => {
      e.preventDefault();
      let ok = true;
      const fields = [
        $("#parent-name"),
        $("#phone"),
        $("#email"),
        $("#password"),
        $("#child-name"),
        $("#year-group"),
        $("#curriculum"),
        $("#area"),
        $("#track"),
      ];
      fields.forEach((f) => {
        if (!validateField(f)) ok = false;
      });
      if (!ok) return;

      const email = $("#email").value.trim().toLowerCase();
      const accounts = getAccounts();
      if (accounts[email]) {
        showError($("#email"), "An account with this email already exists. Please log in.");
        return;
      }

      const data = {
        parentName: $("#parent-name").value.trim(),
        phone: $("#phone").value.trim(),
        email,
        password: $("#password").value, // plain for demo only
        childName: $("#child-name").value.trim(),
        yearGroup: $("#year-group").value,
        curriculum: $("#curriculum").value,
        area: $("#area").value,
        track: $("#track").value,
        needType: $("#need-type")?.value || "",
        notes: $("#notes")?.value || "",
      };
      saveAccount(email, data);
      setUser(data);
      setTrack(data.track);

      // Success animation
      formSignup.classList.add("hidden");
      const success = $("#success-msg");
      success.classList.remove("hidden");
      setTimeout(() => {
        window.location.href = "services.html";
      }, 1800);
    });

    // Login submit
    formLogin.addEventListener("submit", (e) => {
      e.preventDefault();
      const emailEl = $("#login-email");
      const passEl = $("#login-password");
      let ok = true;
      if (!validateField(emailEl)) ok = false;
      if (!validateField(passEl)) ok = false;
      if (!ok) return;

      const email = emailEl.value.trim().toLowerCase();
      const accounts = getAccounts();
      const acc = accounts[email];
      if (!acc || acc.password !== passEl.value) {
        showError(passEl, "Incorrect email or password");
        return;
      }
      setUser(acc);
      setTrack(acc.track);
      window.location.href = "services.html";
    });
  }

  /* ---------- SERVICES PAGE ---------- */
  if ($("#standard-content") || $("#sen-content")) {
    const user = getUser();
    if (!user) {
      window.location.href = "signup.html";
      return;
    }

    // Display user name
    const nameEl = $("#user-display-name");
    if (nameEl) nameEl.textContent = user.parentName || user.email;

    // Show correct track content
    const track = user.track || getTrack() || "standard";
    if (track === "sen") {
      $("#sen-content").classList.remove("hidden");
      document.body.classList.add("sen-mode");
    } else {
      $("#standard-content").classList.remove("hidden");
    }

    // Logout
    $("#logout-btn")?.addEventListener("click", () => {
      clearUser();
      window.location.href = "signup.html";
    });

    // Switch track
    $("#switch-track-btn")?.addEventListener("click", () => {
      const newTrack = track === "sen" ? "standard" : "sen";
      setTrack(newTrack);
      // Update stored user
      const accounts = getAccounts();
      if (accounts[user.email]) {
        accounts[user.email].track = newTrack;
        localStorage.setItem(STORAGE_ACCOUNTS, JSON.stringify(accounts));
      }
      user.track = newTrack;
      setUser(user);
      window.location.reload();
    });
  }

  /* ---------- Scroll reveals (IntersectionObserver) ---------- */
  const reveals = $$(".reveal");
  if (reveals.length && "IntersectionObserver" in window) {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("visible");
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.15, rootMargin: "0px 0px -40px 0px" }
    );
    reveals.forEach((el) => io.observe(el));
  } else {
    reveals.forEach((el) => el.classList.add("visible"));
  }

  /* ---------- Price counters ---------- */
  const counters = $$(".counter");
  if (counters.length && "IntersectionObserver" in window) {
    const counterIo = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          const el = entry.target;
          const target = parseInt(el.dataset.target, 10);
          const duration = 1200;
          const start = performance.now();
          function tick(now) {
            const progress = Math.min((now - start) / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3); // ease-out cubic
            el.textContent = Math.round(target * eased);
            if (progress < 1) requestAnimationFrame(tick);
          }
          requestAnimationFrame(tick);
          counterIo.unobserve(el);
        });
      },
      { threshold: 0.5 }
    );
    counters.forEach((c) => counterIo.observe(c));
  }
})();
