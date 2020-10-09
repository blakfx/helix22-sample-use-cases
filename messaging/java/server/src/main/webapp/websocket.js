var Device;
var ChatMsg;
var useJSON = false;
var socket;

window.onload = function() {
    protobuf.load("device.proto", function(err, root) {
        if (err)
            throw err;
    
        // Obtain a message type, and store it
        Device = root.lookupType("device.Device");
        ChatMsg = root.lookupType("device.ChatMsg");
        socket = new WebSocket("ws://localhost:8080/server/actions");

        socket.onopen = function() {
            console.log("DBG: Connected successfully!");
        };
        
        socket.onclose = function() {   
            console.log("DBG: Closing socket!");
        }
        
        socket.onerror = function() {
            console.log("DBG: error!");
        }
        
        socket.onmessage = function onMessage(event) {
            console.log("message received!");
            console.log(event.data);
        
            var device = null;
        
            try {
                device = JSON.parse(event.data);
                handleDeviceMessage(device);
            } catch (error) {
                console.log("Unable to parse received message as JSON.. Using proto-buffers");
                getPBMessageDevice(event).then(handleDeviceMessage);
            }
        }
    });
};

async function getPBMessageDevice(event) {
    var ab = await new Response(event.data).arrayBuffer();
    ab = new Uint8Array(ab);
    return Device.decode(ab);
}

function handleDeviceMessage(device) {
    if (device.action === "add") {
        printDeviceElement(device);
    }
    if (device.action === "remove") {
        document.getElementById(device.id).remove();
        //device.parentNode.removeChild(device);
    }
    if (device.action === "toggle") {
        var node = document.getElementById(device.id);
        var statusText = node.children[2];
        if (device.status === "On") {
            statusText.innerHTML = "Status: " + device.status + " (<a href=\"#\" OnClick=toggleDevice(" + device.id + ")>Turn off</a>)";
        } else if (device.status === "Off") {
            statusText.innerHTML = "Status: " + device.status + " (<a href=\"#\" OnClick=toggleDevice(" + device.id + ")>Turn on</a>)";
        }
    }
    if (device.action === "chat") {
        console.log(device.message.content);
    }
}

function addDevice(name, type, description) {
    if(useJSON) {
        console.log("adding the device..");
        var device = {
            action: "add",
            name: name,
            type: type,
            description: description
        };
        device = JSON.stringify(device);
    }
    else {
        console.log("adding the device (PB)..")
        var payload = { action: "add", name: name, type: type, description: description };
        var message = Device.create(payload);
        device = Device.encode(message).finish();
    }
    socket.send(device);
}

function removeDevice(element) {
    if(useJSON) {
        console.log("removing the device..");
        var device = {
            action: "remove",
            id: element
        };
        device = JSON.stringify(device); 
    }
    else {
        console.log("removing the device (PB)..")
        var payload = { action: "remove", id: element };
        var message = Device.create(payload);
        device = Device.encode(message).finish();
    }
    socket.send(device);
}

function toggleDevice(element) {
    if(useJSON) {
        console.log("toggling the device..");
        var device = {
            action: "toggle",
            id: element
        };
        device = JSON.stringify(device);
    }
    else {
        console.log("toggling the device (PB)..")
        var payload = { action: "toggle", id: element };
        var message = Device.create(payload);
        device = Device.encode(message).finish();
    }
    socket.send(device);
}

function printDeviceElement(device) {
    var content = document.getElementById("content");
    
    var deviceDiv = document.createElement("div");
    deviceDiv.setAttribute("id", device.id);
    deviceDiv.setAttribute("class", "device " + device.type);
    content.appendChild(deviceDiv);

    var deviceName = document.createElement("span");
    deviceName.setAttribute("class", "deviceName");
    deviceName.innerHTML = device.name;
    deviceDiv.appendChild(deviceName);

    var deviceType = document.createElement("span");
    deviceType.innerHTML = "<b>Type:</b> " + device.type;
    deviceDiv.appendChild(deviceType);

    var deviceStatus = document.createElement("span");
    if (device.status === "On") {
        deviceStatus.innerHTML = "<b>Status:</b> " + device.status + " (<a href=\"#\" OnClick=toggleDevice(" + device.id + ")>Turn off</a>)";
    } else if (device.status === "Off") {
        deviceStatus.innerHTML = "<b>Status:</b> " + device.status + " (<a href=\"#\" OnClick=toggleDevice(" + device.id + ")>Turn on</a>)";
        //deviceDiv.setAttribute("class", "device off");
    }
    deviceDiv.appendChild(deviceStatus);

    var deviceDescription = document.createElement("span");
    deviceDescription.innerHTML = "<b>Comments:</b> " + device.description;
    deviceDiv.appendChild(deviceDescription);

    var removeDevice = document.createElement("span");
    removeDevice.setAttribute("class", "removeDevice");
    removeDevice.innerHTML = "<a href=\"#\" OnClick=removeDevice(" + device.id + ")>Remove device</a>";
    deviceDiv.appendChild(removeDevice);
}

function showForm() {
    document.getElementById("addDeviceForm").style.display = '';
}

function hideForm() {
    document.getElementById("addDeviceForm").style.display = "none";
}

function formSubmit() {
    var form = document.getElementById("addDeviceForm");
    var name = form.elements["device_name"].value;
    var type = form.elements["device_type"].value;
    var description = form.elements["device_description"].value;
    hideForm();
    document.getElementById("addDeviceForm").reset();
    addDevice(name, type, description);
}

function init() {
    hideForm();
}

                                