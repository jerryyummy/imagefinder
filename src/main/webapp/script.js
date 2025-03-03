document.addEventListener("DOMContentLoaded", function() {
    var normalImageList = document.querySelector('#normalImageList');
    var logoImageList = document.querySelector('#logoImageList');
    var urlInput = document.querySelector('#urlInput');
    var errorMessage = document.querySelector('#errorMessage');
    var loadingMessage = document.querySelector('#loadingMessage');
    var statsMessage = document.querySelector('#statsMessage');
    var submitBtn = document.querySelector('#submitBtn');

    function apiCallBack(xhr, callback) {
        if (xhr.readyState === XMLHttpRequest.DONE) {
            loadingMessage.style.display = "none";

            if (xhr.status !== 200) {
                let message = xhr.status + ":" + xhr.statusText + ":" + xhr.responseText;
                errorMessage.textContent = "Error: " + message;
                return;
            }
            let responseText = xhr.responseText ? JSON.parse(xhr.responseText) : null;
            if (callback) {
                callback(responseText);
            }
        }
    }

    function updateList(response) {
        normalImageList.innerHTML = '';
        logoImageList.innerHTML = '';
        errorMessage.textContent = '';
        statsMessage.textContent = '';

        if (!response || (!response.images && !response.logos)) {
            errorMessage.textContent = "No images found.";
            return;
        }

        let images = response.images ? JSON.parse(response.images) : [];
        let logos = response.logos ? JSON.parse(response.logos) : [];

        statsMessage.textContent = `Total images found: ${images.length + logos.length} (Normal: ${images.length}, Logos: ${logos.length})`;

        images.forEach(imgUrl => {
            var img = document.createElement("img");
            img.src = imgUrl;
            normalImageList.appendChild(img);
        });

        logos.forEach(imgUrl => {
            var img = document.createElement("img");
            img.src = imgUrl;
            logoImageList.appendChild(img);
        });
    }

    let makeApiCall = function (url, method, obj, callback) {
        let xhr = new XMLHttpRequest();
        xhr.open(method, url);
        xhr.onreadystatechange = apiCallBack.bind(null, xhr, callback);
        xhr.send(obj ? JSON.stringify(obj) : null);
    }

    function checkCrawlStatus(url) {
        let retryCount = 0;
        let maxRetries = 10;
        let interval = 3000;

        function poll() {
            makeApiCall('main?url=' + encodeURIComponent(url), 'GET', null, function(response) {
                if (response.status === "completed") {
                    updateList(response);
                } else if (response.status === "error") {
                    errorMessage.textContent = "Crawling failed: " + response.message;
                } else if (response.status === "in_progress") {
                    if (retryCount < maxRetries) {
                        retryCount++;
                        setTimeout(poll, interval);
                    } else {
                        errorMessage.textContent = "Crawling is taking longer than expected. Please check again later.";
                    }
                } else {
                    errorMessage.textContent = "Unexpected status: " + response.status;
                }
            });
        }

        poll();
    }

    submitBtn.addEventListener("click", function(event) {
        event.preventDefault();
        let url = urlInput.value.trim();

        if (!url || !url.startsWith("http")) {
            errorMessage.textContent = "Please enter a valid URL.";
            return;
        }

        errorMessage.textContent = '';
        statsMessage.textContent = '';
        loadingMessage.style.display = "block";
        normalImageList.innerHTML = '';
        logoImageList.innerHTML = '';

        makeApiCall('main?url=' + encodeURIComponent(url), 'POST', null, function(response) {
            if (response.status === "in_progress") {
                checkCrawlStatus(url); // 触发轮询，等待爬取完成
            } else {
                updateList(response);
            }
        });
    });

    urlInput.addEventListener("keypress", function(event) {
        if (event.key === "Enter") {
            submitBtn.click();
        }
    });
});
