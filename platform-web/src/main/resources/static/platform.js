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
        toast.className = 'alert shadow-lg animate-in slide-in-from-right-10 fade-in duration-300 ' + (type === 'error' ? 'alert-error' : 'alert-success');
        toast.style.marginBottom = '0.75rem';
        toast.style.pointerEvents = 'auto';

        var icon = type === 'error' ? 'ri-error-warning-fill' : 'ri-checkbox-circle-fill';

        var row = document.createElement('div');
        row.style.cssText = 'display: flex; align-items: center; gap: 0.75rem;';

        var iconEl = document.createElement('i');
        iconEl.className = icon;
        iconEl.style.fontSize = '1.25rem';

        var textEl = document.createElement('div');

        var titleEl = document.createElement('strong');
        titleEl.style.cssText = 'display: block; font-size: 0.9rem;';
        titleEl.textContent = type === 'error' ? errorLabel : successLabel;

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

document.addEventListener('htmx:configRequest', function (event) {
    var token = document.querySelector('meta[name="csrf-token"]');
    if (token) {
        event.detail.headers['X-CSRF-Token'] = token.content;
    }
});

document.addEventListener('submit', function (event) {
    var form = event.target.closest('form[data-confirm-submit]');
    if (form && !window.confirm(form.dataset.confirmSubmit)) {
        event.preventDefault();
        event.stopImmediatePropagation();
    }
}, true);

document.addEventListener('change', function (event) {
    var control = event.target.closest('[data-submit-on-change]');
    if (control && control.form) {
        control.form.requestSubmit();
    }
});

document.addEventListener('click', function (event) {
    var action = event.target.closest('[data-remove-target], [data-dismiss-overlay], [data-dialog-action], [data-copy-target], [data-copy-text], [data-download-text], [data-uncheck-target], [data-htmx-trigger-target]');
    if (!action) {
        return;
    }

    if (action.dataset.dismissOverlay !== undefined) {
        if (event.target === action) {
            action.remove();
        }
        return;
    }

    if (action.dataset.removeTarget) {
        var removeTarget = document.getElementById(action.dataset.removeTarget);
        if (removeTarget) {
            removeTarget.remove();
        }
    }

    if (action.dataset.dialogAction) {
        var dialog = document.getElementById(action.dataset.dialogTarget);
        if (dialog) {
            dialog[action.dataset.dialogAction]();
        }
    }

    if (action.dataset.copyTarget) {
        var copyTarget = document.getElementById(action.dataset.copyTarget);
        if (copyTarget) {
            void navigator.clipboard.writeText(copyTarget.textContent || '');
        }
    } else if (action.dataset.copyText !== undefined) {
        void navigator.clipboard.writeText(action.dataset.copyText);
    }

    if (action.dataset.downloadText !== undefined) {
        var blob = new Blob([action.dataset.downloadText], { type: 'text/plain' });
        var link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = action.dataset.downloadFilename;
        link.click();
        URL.revokeObjectURL(link.href);
    }

    if (action.dataset.uncheckTarget) {
        var checkbox = document.getElementById(action.dataset.uncheckTarget);
        if (checkbox) {
            checkbox.checked = false;
        }
    }

    if (action.dataset.htmxTriggerTarget) {
        htmx.trigger(action.dataset.htmxTriggerTarget, action.dataset.htmxTriggerEvent);
    }
}, true);

(function () {
    var hasCookie = document.cookie.split(';').some(function (c) {
        return c.trim().startsWith('app_theme=');
    });
    if (!hasCookie && window.matchMedia) {
        var prefersLight = window.matchMedia('(prefers-color-scheme: light)').matches;
        if (prefersLight) {
            var expires = new Date(Date.now() + 365 * 86400 * 1000).toUTCString();
            document.cookie = 'app_theme=light;path=/;expires=' + expires;
            window.location.reload();
        }
    }
})();
