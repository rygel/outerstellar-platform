// Toast notification handler
(function () {
    var errorLabel = document.body.dataset.toastError;
    var successLabel = document.body.dataset.toastSuccess;

    document.addEventListener('htmx:responseError', function (event) {
        var errorBody = event.detail.xhr.responseText || 'An unexpected error occurred';
        showToast(errorBody, 'error');
    });

    window.showToast = function (message, type) {
        var container = document.getElementById('toast-container');
        var toast = document.createElement('div');
        toast.className = 'panel animate-in slide-in-from-right-10 fade-in duration-300 ' + (type === 'error' ? 'panel-danger' : 'panel-success');
        toast.style.padding = '0.75rem 1.25rem';
        toast.style.marginBottom = '0.75rem';
        toast.style.pointerEvents = 'auto';
        toast.style.boxShadow = '0 10px 15px -3px rgba(0, 0, 0, 0.1)';

        var title = type === 'error' ? errorLabel : successLabel;
        var icon = type === 'error' ? 'ri-error-warning-fill' : 'ri-checkbox-circle-fill';

        var row = document.createElement('div');
        row.style.cssText = 'display: flex; align-items: center; gap: 0.75rem;';

        var iconEl = document.createElement('i');
        iconEl.className = icon;
        iconEl.style.fontSize = '1.25rem';

        var textEl = document.createElement('div');

        var titleEl = document.createElement('strong');
        titleEl.style.cssText = 'display: block; font-size: 0.9rem;';
        titleEl.textContent = title;

        var msgEl = document.createElement('p');
        msgEl.style.cssText = 'margin:0; font-size: 0.85rem; opacity: 0.9;';
        msgEl.textContent = message;

        textEl.appendChild(titleEl);
        textEl.appendChild(msgEl);
        row.appendChild(iconEl);
        row.appendChild(textEl);
        toast.appendChild(row);

        container.appendChild(toast);
        setTimeout(function () {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(20px)';
            toast.style.transition = 'all 0.3s ease-out';
            setTimeout(function () { toast.remove(); }, 300);
        }, 5000);
    };
})();

// Inject CSRF token into every HTMX request automatically
document.addEventListener('htmx:configRequest', function (event) {
    var token = document.querySelector('meta[name="csrf-token"]');
    if (token) {
        event.detail.headers['X-CSRF-Token'] = token.content;
    }
});

// On first visit (no theme cookie set), apply system color-scheme preference.
// The server defaults to dark; if the user prefers light we redirect once.
(function () {
    var hasCookie = document.cookie.split(';').some(function (c) {
        return c.trim().startsWith('app_theme=');
    });
    if (!hasCookie && window.matchMedia) {
        var prefersLight = window.matchMedia('(prefers-color-scheme: light)').matches;
        if (prefersLight) {
            var expires = new Date(Date.now() + 365 * 86400 * 1000).toUTCString();
            document.cookie = 'app_theme=default;path=/;expires=' + expires;
            window.location.reload();
        }
    }
})();
