(function () {
    const cleanCurrencyValue = (value) => {
        if (value === null || value === undefined) {
            return '';
        }

        const asString = String(value);
        if (!asString) {
            return '';
        }

        const stripped = asString.replace(/[^0-9.]/g, '');
        if (!stripped) {
            return '';
        }

        const parts = stripped.split('.');
        const intPart = parts[0];
        const decimalPart = parts.length > 1 ? parts.slice(1).join('') : '';
        return decimalPart ? `${intPart}.${decimalPart}` : intPart;
    };

    const formatCurrencyDisplay = (cleanValue) => {
        if (!cleanValue) {
            return '';
        }
        const [intPart, decimalPart] = cleanValue.split('.');
        const withGrouping = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
        return decimalPart ? `${withGrouping}.${decimalPart}` : withGrouping;
    };

    const formatInputValue = (input) => {
        const cleaned = cleanCurrencyValue(input.value);
        input.dataset.rawValue = cleaned;
        input.value = formatCurrencyDisplay(cleaned);
    };

    const initCurrencyInputs = (root) => {
        const scope = root || document;
        const inputs = scope.querySelectorAll('[data-currency-input]');
        inputs.forEach((input) => {
            if (input.dataset.currencyFormatterAttached === '1') {
                return;
            }
            input.dataset.currencyFormatterAttached = '1';
            formatInputValue(input);

            input.addEventListener('input', () => {
                const cursorFromEnd = input.value.length - (input.selectionStart || 0);
                formatInputValue(input);
                const newPos = input.value.length - cursorFromEnd;
                input.setSelectionRange(newPos, newPos);
            });

            input.addEventListener('blur', () => formatInputValue(input));
        });
    };

    const stripCurrencyFormattingOnSubmit = (form) => {
        form.querySelectorAll('[data-currency-input]').forEach((input) => {
            input.value = cleanCurrencyValue(input.value);
        });
    };

    window.currencyInputUtils = {
        cleanCurrencyValue,
        formatCurrencyDisplay,
        initCurrencyInputs,
    };

    document.addEventListener('DOMContentLoaded', () => {
        initCurrencyInputs(document);
        document.querySelectorAll('form[data-strip-currency-on-submit]').forEach((form) => {
            form.addEventListener('submit', () => stripCurrencyFormattingOnSubmit(form));
        });
    });
})();
