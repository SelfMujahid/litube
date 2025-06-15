(function () {
    if (!JSON.parse(localStorage.getItem("preferences")).enable_hide_shorts) return;
    if (window.hideShortsInjected) return;

    // rules
    const HIDE_RULES = [
        // reels
        {
            selector: 'ytm-shorts-lockup-view-model',
            closest: 'ytm-reel-shelf-renderer',
        },
        // bottom navigation
        {
            selector: '.pivot-bar-item-tab.pivot-shorts',
            closest: 'ytm-pivot-bar-item-renderer',
        },
        // single short video
        {
            selector: 'a[href^="/shorts/"]',
            closest: 'ytm-video-with-context-renderer',
        },
        // shorts grid
        {
            selector: 'ytm-shorts-lockup-view-model',
            closest: 'ytm-rich-section-renderer',
        }
    ];

    function hideElements() {
        HIDE_RULES.forEach(({ selector, closest }) => {
            document.querySelectorAll(selector).forEach(el => {
                const container = el.closest(closest);
                if (container && container.style.display !== 'none') {
                    container.style.display = 'none';
                }
            });
        });
    }

    hideElements();

    const observer = new MutationObserver((mutations) => {
        for (const mutation of mutations) {
            if (mutation.addedNodes.length > 0) {
                hideElements();
                break;
            }
        }
    });

    observer.observe(document.body, {
        childList: true,
        subtree: true
    });

    window.hideShortsInjected = true;
})();
