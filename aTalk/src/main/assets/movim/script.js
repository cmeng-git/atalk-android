var API = {
    inhibed: false,
    init: function() {
        if(localStorage.mainPod) {
            var url = new URL(localStorage.mainPod);
            document.querySelector('#list input').value = localStorage.mainPod;
            document.querySelector('#redirect h2 span').innerHTML = url.host;
            setTimeout(function() { if(API.inhibed == false) window.location.href = localStorage.mainPod; } , 1500);
        } else {
            API.get();
        }
    },
    inhibit: function() {
        API.inhibed = true;
    },
    get: function() {
        API.inhibit();
        document.querySelector('body').className = 'list';
        var xhr = new XMLHttpRequest();
        xhr.open('GET', 'https://api.movim.eu/pods/favorite', true);

        var list = document.querySelector('#list ul');
        var header = document.querySelector('#list h2');

        xhr.onreadystatechange = function(e) {
            if (this.readyState == 4 && this.status == 200) {
                var result = JSON.parse(this.responseText);
                for(var i = 0, len = result.pods.length; i < len; ++i ) {
                    header.innerHTML = "";
                    var pod = result.pods[i];

                    li = document.createElement("li");
                    list.appendChild(li);

                    var url = new URL(pod.url);
                    li.dataset.url = pod.url;
                    li.dataset.host = url.host;

                    li.onclick = function() {
                        //if(Android != null) Android.showToast('Pod set to ' + this.dataset.host);
                        document.querySelector('#list input[name=url]').value = this.dataset.url;
                    }

                    info = document.createElement("span");
                    info.className = 'info';
                    info.appendChild(document.createTextNode(pod.connected + ' / ' + pod.population));
                    li.appendChild(info);

                    span = document.createElement("span");
                    span.appendChild(document.createTextNode(url.host));
                    li.appendChild(span);

                    p = document.createElement("p");
                    p.appendChild(document.createTextNode(pod.description));
                    li.appendChild(p);
                }
            } else {
                header.innerHTML = "The Pods list can't be reached for the moment";
            }
        };

        xhr.send();
    },
    set: function() {
        var input = document.querySelector('#list input[name=url]');
        if(input.validity.valid) {
            localStorage.mainPod = input.value;
            API.reload();
        }
    },
    reload: function() {
        window.location.reload();
    }
}
