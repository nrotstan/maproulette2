/**
 * Created by cuthbertm on 3/21/16.
 */
marked.setOptions({
    gfm: true,
    tables: true,
    breaks: false,
    pedantic: false,
    sanitize: true,
    smartLists: true,
    smartypants: true
});

toastr.options = {
    "positionClass": "toast-top-center",
    "toastClass": "notification",
    "closeButton": false,
    "debug": false,
    "newestOnTop": false,
    "progressBar": false,
    "preventDuplicates": false,
    "onclick": null,
    "showDuration": "300",
    "hideDuration": "1000",
    "timeOut": "3000",
    "extendedTimeOut": "0",
    "showEasing": "swing",
    "hideEasing": "linear",
    "showMethod": "fadeIn",
    "hideMethod": "fadeOut",
    "tapToDismiss" : false
};

var ToastUtils = {
    /**
     * Gets the notification class based on the sidebar, whether it is collapsed or not
     *
     * @returns {*}
     */
    getPositionClass: function(positionClass) {
        if (typeof positionClass !== 'undefined') {
            return positionClass;
        } else if (!$("#map").length) {
            return 'toast-top-center';
        } else if ($("#sidebar").width() == 50) {
            return 'notification-position';
        } else {
            return 'notification-position-menuopen';
        }
    },
    showToast: function(type, msg, options) {
        if (typeof options === 'undefined' || options === null) {
            options = {};
        }
        if (typeof options.positionClass === 'undefined') {
            options.positionClass = ToastUtils.getPositionClass();
        }
        toastr[type](msg, '', options);
    },
    Info: function(info, options) {
        if (typeof options === 'undefined' || options === null) {
            options = {};
        }
        options.toastClass = 'notification-info toast-info';
        ToastUtils.showToast('info', '<table><tr><td><i class="fa fa-info-circle fa-2x" style="padding-right: 10px;"></i></td><td>' + info + '</td></tr></table>', options);
    },
    Warning: function(warning, options) {
        ToastUtils.showToast('warning', warning, options);
    },
    Error: function(error, options) {
        ToastUtils.showToast('error', error, options);
    },
    Success: function(msg, options) {
        ToastUtils.showToast('success', msg, options);  
    },
    // handles any javascript errors by popping a toast up at the top.
    handleError: function(error) {
        var jsonMsg = JSON.parse(error.responseText);
        ToastUtils.Error(jsonMsg.status + " : " + jsonMsg.message);
    }
};

// Basic namespace for some Util functions used in this js lib
var Utils = {
    addComma: function (str) {
        return (str.match(/\,\s+$/) || str.match(/in\s+$/)) ? '' : ', ';
    },
    getLocation: function(lat, lon, success, error) {
        var mqurl = 'http://nominatim.openstreetmap.org/reverse?format=json&lat=' + lat + '&lon=' + lon;
        $.ajax({
            url: mqurl,
            jsonp: "json_callback",
            success: success,
            error:error
        });
    },
    mqResultToString: function (addr) {
        // Convert a MapQuest reverse geocoding result to a human readable string.
        var out, county, town;
        if (!addr || !(addr.town || addr.county || addr.hamlet || addr.state || addr.country)) {
            return Messages('mapping.js.location.notfound');
        }
        out = Messages('mapping.js.location.start') + ' ';
        if (typeof addr.city !== 'undefined' && addr.city !== null) {
            out += Messages('mapping.js.location.between') + ' ' + addr.city;
        } else if (typeof addr.town !== 'undefined' && addr.town !== null) {
            out += Messages('mapping.js.location.between') + ' ' + addr.town;
        } else if (typeof addr.hamlet !== 'undefined' && addr.hamlet !== null) {
            out += Messages('mapping.js.location.between') + ' ' + addr.hamlet;
        } else {
            out += Messages('mapping.js.location.elsebetween') + ' ';
        }
        out += Utils.addComma(out);
        if (addr.county) {
            if (addr.county.toLowerCase().indexOf('county') > -1) {
                out += addr.county;
            } else {
                out += addr.county + ' County';
            }
        }
        out += Utils.addComma(out);
        if (addr.state) {
            out += addr.state;
        }
        out += Utils.addComma(out);
        if (addr.country) {
            if (addr.country.indexOf('United States') > -1) {
                out += Messages('mapping.js.location.the') + ' ';
            }
            out += addr.country;
        }
        out += '.';
        return out;
    },
    getQSParameterByName: function(name) {
        name = name.replace(/[\[\]]/g, "\\$&");
        var regex = new RegExp("[?&]" + name + "(=([^&#]*)|&|#|$)", "i"),
            results = regex.exec(window.location.href);
        if (!results) return null;
        if (!results[2]) return '';
        return decodeURIComponent(results[2].replace(/\+/g, " "));
    },
    appendQueryString: function(url) {
        var qs = document.URL.split("?");
        var queryString = "";
        if (qs.length > 1) {
            queryString = qs[1];
        }    
        return url + "?" + queryString;
    },
    getDefaultValue: function(property, defaultValue) {
        var returnValue = defaultValue;
        if (typeof property !== 'undefined') {
            returnValue = property;
        }
        return returnValue;
    }
};

/**
 * The search parameters allow tasks to be executed over multiple different projects, challenges
 * and tags. It allows searching of those elements and the next random task retrieved will remain
 * in the bounds of the search parameters. This will be stored in a session cookie to be shared
 * with any requests that go to the server
 *
 * projectId If wanting to limit to a specific project set this value
 * projectSearch Will filter based on the name of the project
 * challengeId If wanting to limit to a specific challenge set this value
 * challengeSearch Will filter based on the name of the challenge. eg. All challenges starting with "c"
 * challengeTags Will filter based on the supplied tags for the challenge
 * taskSearch Will filter based on the name of the task (probably wouldn't be used a lot
 * taskTags Will filter based on the tags of the task
 * props Will filter on the properties of features in the task
 * @constructor
 */
function SearchParameters() {
    var defaultState = {
        projectId: -1,
        projectSearch: '',
        projectEnabled: true,
        challengeId: -1,
        challengeEnabled: true,
        challengeSearch: '',
        challengeTags: [],
        taskSearch: '',
        taskTags: [],
        taskStatus: [],
        props: {},
        priority: -1,
        location: {},
        owner: ''
    };

    var update = function() {
        Cookies.set('search', search);
    };

    var _reset = function() {
        search = defaultState;
        update();
    };

    this.reset = function() {
        _reset();
    };

    var getValue = function(key) {
        return search[key];
    };

    var setValue = function(key, value) {
        search[key] = value;
        update();
    };

    var setValueByQS = function(qsKey, cookieKey, convert) {
        var value = Utils.getQSParameterByName(qsKey);
        if (typeof value !== 'undefined' && value !== "" && value !== null) {
            if (typeof convert !== 'undefined') {
                setValue(cookieKey, convert(value));
            } else {
                setValue(cookieKey, value);
            }
        }
    };

    this.qsUpdate = function(resetCookie) {
        if (resetCookie) {
            _reset();
        }
        setValueByQS("pid", "projectId", parseInt);
        setValueByQS("ps", "projectSearch");
        setValueByQS("pe", "projectEnabled", Boolean);
        setValueByQS("cid", "challengeId", parseInt);
        setValueByQS("ct", "challengeTags", function(v) { return v.split(","); });
        setValueByQS("ctc", "challengeTagConjunction", Boolean);
        setValueByQS("cs", "challengeSearch");
        setValueByQS("ce", "challengeEnabled", Boolean);
        setValueByQS("tt", "taskTags", function(v) { return v.split(","); });
        setValueByQS("ttc", "taskTagConjunction", Boolean);
        setValueByQS("ts", "taskSearch");
        setValueByQS("tStatus", "taskStatus", function(v) { return v.split(",").map(Number); });
        setValueByQS("tp", "priority", parseInt);
        setValueByQS("tbb", "location");
        setValueByQS("fuzzy", "fuzzy", parseInt);
        setValueByQS("o", "owner");
    };
    
    var search = Cookies.getJSON('search');
    if (typeof search === 'undefined' || search == {}) {
        search = defaultState;
    }
    this.qsUpdate();

    this.getCookieString = function() {
        return Cookies.get('search');
    };

    this.getProjectId = function() {
        return getValue("projectId");
    };
    this.setProjectId = function(id) {
        setValue("projectId", id);
    };
    this.getProjectSearch = function() {
        return getValue("projectSearch");
    };
    this.setProjectSearch = function(search) {
        setValue("projectSearch", search);
    };
    this.getProjectEnabled = function() {
        return getValue("projectEnabled");
    };
    this.setProjectEnabled = function(enabled) {
        setValue("projectEnabled", enabled);
    };
    this.getChallengeId = function() {
        return getValue("challengeId");
    };
    this.setChallengeId = function(id) {
        setValue("challengeId", id);
    };
    this.getChallengeTags = function() {
        return getValue("challengeTags");
    };
    this.setChallengeTags = function(tags) {
        if (typeof tags === 'string') {
            tags = tags.split(",");
        }
        setValue("challengeTags", tags);
    };
    this.getChallengeTagConjunction = function() {
        return getValue("challengeTagConjunction");
    };
    this.setChallengeTagConjunction = function(conjunction) {
        setValue("challengeTagConjunction", conjunction);
    };
    this.getChallengeSearch = function() {
        return getValue("challengeSearch");
    };
    this.setChallengeSearch = function(search) {
        setValue("challengeSearch", search);
    };
    this.getChallengeEnabled = function() {
        return getValue("challengeEnabled");
    };
    this.setChallengeEnabled = function(enabled) {
        setValue("challengeEnabled", enabled);
    };
    this.getTaskTags = function() {
        return getValue("taskTags");
    };
    this.setTaskTags = function(tags) {
        if (typeof tags === 'string') {
            tags = tags.split(",");
        }
        setValue("taskTags", tags);
    };
    this.getTaskTagConjunction = function() {
        return getValue("taskTagConjunction");
    };
    this.setTaskTagConjunction = function(conjunction) {
        setValue("taskTagConjunction", conjunction);
    };
    this.getTaskSearch = function() {
        return getValue("taskSearch");
    };
    this.setTaskSearch = function(search) {
        setValue("taskSearch", search);
    };
    this.getTaskStatus = function() {
        return getValue("taskStatus");
    };
    this.setTaskStatus = function(status) {
        if (typeof status === 'string') {
            status = status.split(",").map(Number);
        }
        setValue("taskStatus", status);
    };
    this.getPriority = function() {
        return getValue("priority");
    };
    this.setPriority = function(priority) {
        setValue("priority", priority);
    };
    this.getSearchLocation = function() {
        return getValue("location");
    };
    this.setSearchLocation = function(left, bottom, right, top) {
        setValue("location", {left:left, bottom:bottom, right:right, top:top});
    };
    this.getFuzzySearch = function() {
        return getValue("fuzzy");
    };
    this.setFuzzySearch = function(fuzzy) {
        setValue("fuzzy", fuzzy);
    };
    this.getOwner = function() {
        return getValue("owner");
    };
    this.setOwner = function(owner) {
        setValue("owner", owner);
    };
}

/**
 * Helper for the generic delete function, this will delete a specific project
 * Only an Admin user for the specific project will be able to delete the project or Super user
 * 
 * @param itemId The id of the project
 */
var deleteProject = function(itemId) {
    deleteItem("Project", itemId);
};

/**
 * Helper for the generic delete function, this will delete a specific challenge
 * Only an Admin user for the specific parent project will be able to delete the challenge or Super user
 * 
 * @param itemId The id of the challenge
 */
var deleteChallenge = function(itemId) {
    deleteItem("Challenge", itemId);
};

/**
 * Helper for the generic delete function, this will delete a specific task
 * Only an Admin user for the specific parent project will be able to delete the task or Super user
 * 
 * @param itemId The id of the task
 */
var deleteTask = function(itemId) {
    deleteItem("Task", itemId);
};

/**
 * Helper for the generic delete function, this will delete a specific user
 * Only a super user can delete users
 * 
 * @param itemId The id of the user
 */
var deleteUser = function(itemId) {
    deleteItem("User", itemId);
};

/**
 * The generic delete function that makes the API request to the server
 * 
 * @param itemType The type of object you are deleting
 * @param itemId The id of the object you are deleting
 */
var deleteItem = function(itemType, itemId) {
    var apiCallback = {
        success : function(data) {
            //todo: probably might want to do something with the data
            location.reload();
        },
        error : function(error) {
            ToastUtils.Error(error.responseJSON.message);
        }
    };

    ToastUtils.Info(Messages('main.js.deletion') + " " + itemType + " [" + itemId + "]");
    if (itemType == "Project") {
        jsRoutes.org.maproulette.controllers.api.ProjectController.delete(itemId).ajax(apiCallback);
    } else if (itemType == "Challenge") {
        jsRoutes.org.maproulette.controllers.api.ChallengeController.delete(itemId).ajax(apiCallback);
    } else if (itemType == "Task") {
        jsRoutes.org.maproulette.controllers.api.TaskController.delete(itemId).ajax(apiCallback);
    } else if (itemType == "User") {
        jsRoutes.controllers.AuthController.deleteUser(itemId).ajax(apiCallback);
    }
};

/**
 * Helper function to specifically find projects, see generic find function for information on parameters
 */
var findProjects = function(search, parentId, limit, offset, onlyEnabled, handler, errorHandler) {
    findItem("Project", search, parentId, limit, offset, onlyEnabled, handler, errorHandler);  
};

/**
 * Helper function to specifically find challenges, see generic find function for information on parameters
 */
var findChallenges = function(search, parentId, limit, offset, onlyEnabled, handler, errorHandler) {
    findItem("Challenge", search, parentId, limit, offset, onlyEnabled, handler, errorHandler);
};

/**
 * Helper function to specifically find tags, see generic find function for information on parameters
 */
var findTags = function(search, limit, offset, handler, errorHandler) {
    findItem("Tag", search, -1, limit, offset, true, handler, errorHandler);
};

/**
 * A generic find function that will make an API call to the server to search for a specific object 
 * and return a list of objects that match the search criteria
 * 
 * @param itemType The type of object you are searching for
 * @param search The search phrase that will match against the name of the object
 * @param limit The number of objects you want returned in the array at one time
 * @param offset A paging mechanism, starting at 0
 * @param onlyEnabled If true then only enabled objects will be returned
 * @param handler The javascript handler that will handle the results
 * @param errorHandler The javascript handler that will handle any failure
 */
var findItem = function(itemType, search, parentId, limit, offset, onlyEnabled, handler, errorHandler) {
    var apiCallback = {
        success: handler,
        error: errorHandler
    };
    if (itemType == "Project") {
        jsRoutes.org.maproulette.controllers.api.ProjectController.find(search, parentId, limit, offset, onlyEnabled).ajax(apiCallback);
    } else if (itemType == "Challenge") {
        jsRoutes.org.maproulette.controllers.api.ChallengeController.find(search, parentId, limit, offset, onlyEnabled).ajax(apiCallback);
    } else if (itemType == "Tag") {
        jsRoutes.org.maproulette.controllers.api.TagController.getTags(search, limit, offset).ajax(apiCallback);
    }
};

/**
 * Helper function to specifically get a project, see generic get function for information on parameters
 */
var getProject = function(projectId, handler, errorHandler) {
    getItem("Project", projectId, handler, errorHandler);
};

/**
 * Helper function to specifically get a challenge, see generic get function for information on parameters
 */
var getChallenge = function(challengeId, handler, errorHandler) {
    getItem("Challenge", challengeId, handler, errorHandler);
};

/**
 * A generic get function that will make an API call to the server to get a specific object
 *
 * @param itemType The type of object you are trying to retrieve
 * @param itemId The id of the object
 * @param handler The javascript handler that will handle the results
 * @param errorHandler The javascript handler that will handle any failure
 */
var getItem = function(itemType, itemId, handler, errorHandler) {
    var apiCallback = {
        success: handler,
        error: errorHandler
    };
    if (itemType == "Project") {
        jsRoutes.org.maproulette.controllers.api.ProjectController.read(itemId).ajax(apiCallback);
    } else if (itemType == "Challenge") {
        jsRoutes.org.maproulette.controllers.api.ChallengeController.read(itemId).ajax(apiCallback);
    }
};

/**
 * Adds a task to the map, if the map is not on the current page it will load that page by 
 * redirecting, if it is on the page then it will simply load it dynamically.
 * 
 * @param parentId
 * @param taskId
 */
var addItemToMap = function(parentId, taskId) {
    if ($("#map").length) {
        MRManager.addTaskToMap(parentId, taskId);    
    } else {
        if (typeof taskId === 'undefined' || taskId == -1) {
            location.href = Utils.appendQueryString("/map/" + parentId);
        } else {
            location.href = Utils.appendQueryString("/map/" + parentId + "/" + taskId);
        }
    }
};

var generateAPIKey = function(success, userId) {
    var apiCallback = {
        success : function(data) {
            currentAPIKey = data;
            if (typeof success === 'undefined') {
                showAPIKey();
            } else {
                success(data);
            }
        },
        error : Utils.handleError
    };
    //ToastUtils.Info(Messages('main.js.api.generation', userId));
    jsRoutes.controllers.AuthController.generateAPIKey(userId).ajax(apiCallback);
};

var rebuildChallenge = function(parentId, challengeId, success) {
    ToastUtils.Info(Messages('main.js.api.rebuilding', challengeId));
    jsRoutes.controllers.FormEditController.rebuildChallenge(parentId, challengeId).ajax({
      success: function(data) {
          if (typeof success === 'undefined') {
            location.reload();
          } else {
            success(data);   
          }
      },
      error : Utils.handleError
  });
};

var resetTaskInstructions = function(challengeId, success) {
    ToastUtils.Info(Messages('main.js.api.resetinginstructions', challengeId));
    jsRoutes.org.maproulette.controllers.api.ChallengeController.resetTaskInstructions(challengeId).ajax({
        success: function(data) {
            if (typeof success === 'undefined') {
                ToastUtils.Info(Messages('main.js.api.resetinginstructions.success', challengeId));
            } else {
                success(data);
            }
        }
    });
};

var showAPIKey = function() {
    toastr.info(currentAPIKey);
};

var initializeComments = function(taskId, commentId, placement) {
    var populateComments = function(data, dialogID) {
        var $comments = Messages("mapping.js.task.comments.none");
        if (data.length > 0) {
            $comments = $("<table/>");
            for (var i in data) {
                var $row = $('<tr/>');
                var $rowTD = $('<td/>');
                $rowTD.append("<a target='_blank' href='http://osm.org/user/" + data[i].osm_username + "'>" + data[i].osm_username + "</a>" +
                    "<a target='_blank' href='https://www.openstreetmap.org/message/new/" + data[i].osm_username + "'> <i class='fa fa-commenting-o' aria-hidden='true'></i></a> - ");
                $rowTD.append("<i class='small'>" + moment(data[i].created).format("MM/DD/YY h:mm:ssa") + "</i>");
                $rowTD.append("<br/>" + data[i].comment);
                $row.append($rowTD);
                $comments.append($row);
            }
        }
        $("#" + dialogID).empty();
        $("#" + dialogID).append($comments);
    };

    $("#" + commentId).popover({
        title: Messages("mapping.js.task.comments") + " <span class='pull-right'><i class='fa fa-times'></i></span>",
        content: function() {
            var dialogID = "comment-dialog-" + taskId;
            jsRoutes.org.maproulette.controllers.api.TaskController.retrieveComments(taskId).ajax({
                success: function(data) { populateComments(data, dialogID); },
                error: function(data) {
                    $("#" + dialogID).empty();
                    $("#" + dialogID).append(Messages("mapping.js.task.comments.loading.error"));
                }
            });
            return '<div id="' + dialogID + '" class="comment-dialog">Loading comments...</div>';
        },
        html: true,
        placement: placement
    });
};
