var path = window.location.href;
var pathid = path.substr(path.lastIndexOf("/") + 1);

var tagDTOS = {};
var topicDTO = {
    id: isNaN(pathid)?null:pathid,
    tag: "",
    topicType: 3 // 帖子类型默认为广场
};

var condition = "";

var vue = new Vue({
    el: "#publish",
    data: {
        tagDTOS, topicDTO, condition
    },
    mounted: function () { // vue对象加载完毕
        this.removeStorage();
        // 加载md编辑器
        var editor = editormd("topic-editor", {
            width: "100%",
            height: 400,
            path: "/editor/lib/",
            watch: false,
            placeholder: '请输入详细的帖子内容',
            saveHTMLToTextarea: true,
            imageUpload: true,
            imageFormats: ["jpg", "jpeg", "gif", "png", "bmp", "webp"],
            imageUploadURL: "/file/imgUpload"
        });
        this.getTags();
        this.getTopic(topicDTO.id);
    },
    methods: {
        // 移除apps页面存储的page、type信息
        removeStorage:function(){
            window.sessionStorage.removeItem("page");
            window.sessionStorage.removeItem("type");
        },
        getTags: function () {
            var url = "/publish/getTags";
            axios.post(url).then(function (response) {
                var jsonResult = response.data;
                if (jsonResult.success) {
                    vue.tagDTOS = jsonResult.data;
                }
            })
        },
        getTopic: function (id) {
            if (id == null || id == '') {
                return;
            }
            var params = {
                id:id
            };
            var url = "/publish/getTopic";
            axios.post(url,params).then(function (response) {
                var jsonResult = response.data;
                if (jsonResult.success) {
                    vue.topicDTO = jsonResult.data;
                }
            })
        },
        doPublish: doPublish,
        loadTagPanel: loadTagPanel,
        closeTagPanel: closeTagPanel,
        addTag: addTag,
        // search相关函数，函数从文件commonSearch.js中引入
        searchUser:searchUser,
        searchSteam:searchSteam,
        searchTopic:searchTopic
    }
});


function doPublish() {
    // 进行表格内容空校验
    var flag = doCheckValue();
    if (!flag) { // flag为 false 说明有数据为空
        return false;
    }
    // 判断从url获取的id是否是数字
    if(isNaN(vue.topicDTO.id)){
        vue.topicDTO.id=null;
    }
    var url = "/publish";
    axios.post(url, vue.topicDTO).then(function (response) {
        var jsonResult = response.data;
        if (jsonResult.success) {
            if (!vue.topicDTO.id && vue.topicDTO.id != null && vue.topicDTO.id != "") {
                location.href = "/topic/" + vue.topicDTO.id;
            } else {
                location.href = "/forum";
            }
        } else {
            if (jsonResult.code == 2004) { // 用户未进行登录操作
                window.location.href = "/login";
            } else {
                if (jsonResult.code == 2006) { // 标签存在非法项目
                    var msg = "存在非法的标签：[";
                    for (var i = 0; i < jsonResult.data.length; i++) {
                        if (i != jsonResult.data.length - 1)
                            msg += jsonResult.data[i] + "，";
                        else
                            msg += jsonResult.data[i] + "]";
                    }
                    layer.msg(msg);
                } else {
                    layer.msg(jsonResult.message);
                }
            }
        }
    });
}

// 进行数据非空校验
function doCheckValue() {
    var flag = true;
    // 取出html内容，直接在页面展示
    vue.topicDTO.description = $(".editormd-html-textarea").val();

    if (!vue.topicDTO.title || vue.topicDTO.title === "") {
        $("#title-warning").show();
        flag = false;
    } else {
        $("#title-warning").hide();
    }

    if (!vue.topicDTO.description || vue.topicDTO.description === "") {
        $("#des-warning").show();
        flag = false;
    } else {
        $("#des-warning").hide();
    }

    if (!vue.topicDTO.tag || vue.topicDTO.tag === "") {
        $("#tag-warning").show();
        flag = false;
    } else {
        $("#tag-warning").hide();
    }
    return flag;
}

function loadTagPanel() {
    $(".tag-panel").show();
}

function closeTagPanel() {
    $(".tag-panel").hide();
}

function addTag(tag) {
    var content = vue.topicDTO.tag;

    if (content) { // 如果不为空
        // 判断是否重复
        if (content.indexOf(tag) == -1) { // 不重复
            content = content + ',' + tag;
        } else {
            return false;
        }
    } else {
        content += tag;
    }

    vue.topicDTO.tag = content;
}