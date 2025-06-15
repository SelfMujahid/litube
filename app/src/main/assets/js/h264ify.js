(function(){

    if (!JSON.parse(localStorage.getItem("preferences")).enable_h264ify) return;
    if (window.h264ifyInjected) return;


    const disallowedTypes = [
        'vp8',
        'vp9',
        'vp09',
        'av01',
        'av99'
    ];

    
    function makeChecker(orig) {
        return function (type) {
        if (!type) return '';
        for (const blocked of disallowedTypes) {
            if (type.includes(blocked)) return '';
        }
        return orig(type);
        };
    }
    
    const video = document.createElement('video');
    video.__proto__.canPlayType = makeChecker(video.canPlayType.bind(video));
    
    if (window.MediaSource?.isTypeSupported) {
        window.MediaSource.isTypeSupported = makeChecker(window.MediaSource.isTypeSupported.bind(window.MediaSource));
    }
      

    window.h264ifyInjected = true;


})()