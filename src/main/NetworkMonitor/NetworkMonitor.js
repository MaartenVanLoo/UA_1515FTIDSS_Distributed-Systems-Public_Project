let ipNS = "localhost";
let nameServerData = {};
let nodeData = {};
let nodeLastPing = {};
let selectedNode = null;
let nameServerUpdateInterval = 2000;
let nodeUpdateInterval = 5000;
setInterval(updateNameServerData, nameServerUpdateInterval);
setInterval(updateNodeData, nodeUpdateInterval);

//init
statusTableTimeout();
nodesTableTimeout();
clearDetails();
addRowHandlers();

async function updateNameServerData(){
    //fetch data from name server at localhost port 8081
    try {
        response = await GetData("http://"+ ipNS+":8081/ns");
        //log to console
        console.log(response);
        //update name server data
        nameServerData = response;

        statusTableUpdate(response);
        nodesTableUpdate(response);
    }
    catch(e){
        statusTableTimeout();
        nodesTableTimeout();
    }

}
async function GetData(url = '') {
    // Default options are marked with *
    try {
        const response = await fetch(url, {
            method: 'GET', // *GET, POST, PUT, DELETE, etc.
            mode: 'cors', // no-cors, *cors, same-origin
            cache: 'no-cache', // *default, no-cache, reload, force-cache, only-if-cached
            credentials: 'omit', // include, *same-origin, omit
            headers: {},
            redirect: 'follow', // manual, *follow, error
            referrerPolicy: 'no-referrer', // no-referrer, *no-referrer-when-downgrade, origin, origin-when-cross-origin, same-origin, strict-origin, strict-origin-when-cross-origin, unsafe-url
        })

        let body = await response.json(); // parses JSON response into native JavaScript object
        return body;
    }
    catch(e){
        //console.log(e);
        return undefined;
    }
}
const getNodeData = async ({ips}) =>{
    const nodes = ips.map(async ip =>{
        if (ip == "Unknown") return undefined;
        const data = await GetData("http://"+ ip +":8081/node");
        return data;
    })
    return Promise.all(nodes);
}
async function updateNodeData(){
    // get node ip's from table
    let nodes = document.getElementById("nodesTable").rows;
    let nodeIDs = [];
    let nodeIPs = [];
    for(let i = 2; i < nodes.length; i++){
        nodeIDs.push(nodes[i].cells[1].innerHTML);
        nodeIPs.push(nodes[i].cells[2].innerHTML);
    }
    console.log(nodeIPs);

    //fetch data from nodes
    const data =  await getNodeData({ips : nodeIPs});
    for (let i = 0; i < data.length; i++){
        if (data[i] !== undefined && data[i] != null){
            nodeData[nodeIDs[i]] = data[i];
            nodeLastPing[nodeIDs[i]] = Date.now();
        }else if (!(nodeIDs[i] in nodeLastPing)){
            nodeData[nodeIDs[i]] = data[i];
            nodeLastPing[nodeIDs[i]] = Date.now();
        }
    }
    console.log(nodeData);

    // cleanup nodes when ping is too long ago
    //loop over every element in nodeLastPing
    for (let key in nodeLastPing){
        age = Date.now() - nodeLastPing[key];
        console.log(age);
        if ( !nodeIDs.includes(key)){
            console.log("Node " + nodeIDs[i] + " is removed from data");
            delete nodeData[key];
            delete nodeLastPing[key];
        }
        if (age > 2* nodeUpdateInterval){
            //look for index of node in table
            let index = nodeIDs.indexOf(key);
            //set warning flag in Node table
            if (index >= 0 && index < nodes.length-2){
                if (nodeIPs[index] !== "Unknown"){
                    nodes[index+2].cells[3].innerHTML = "ONLINE <img src='images/WARNING.png' alt='OK' height='15' width='15'>";
                }
            }

        }
    }
    updateDetails()
}

function statusTableUpdate(jsonData){
    //update the table
    let statusTable = document.getElementById("statusTable");
    let discovery = jsonData["Utilities"]["Discovery"];
    let nodes = jsonData["Nodes"];

    for (var i = 0; i < statusTable.rows.length; i++) {
        if (statusTable.rows[i].cells[0].innerHTML === "Status") {
            statusTable.rows[i].cells[1].innerHTML = "ONLINE <img src='images/OK.png' alt='OK' height='15' width='15'>";
        }
        else if (statusTable.rows[i].cells[0].innerHTML === "Nodes"){
            statusTable.rows[i].cells[1].innerHTML = nodes;
        }
        else if (statusTable.rows[i].cells[0].innerHTML === "Discovery"){
            if (discovery.includes("enabled")){
                statusTable.rows[i].cells[1].innerHTML = "ENABLED <img src='images/OK.png' alt='OK' height='15' width='15'>";
            }
            else{
                statusTable.rows[i].cells[1].innerHTML = "DISABLED <img src='images/ERROR.png' alt='NOK' height='15' width='15'>";
            }
        }
    }
}
function statusTableTimeout(){
    //update the table
    let statusTable = document.getElementById("statusTable");
    for (var i = 0; i < statusTable.rows.length; i++) {
        if (statusTable.rows[i].cells[0].innerHTML === "Status") {
            statusTable.rows[i].cells[1].innerHTML = "OFFLINE <img src='images/ERROR.png' alt='NOK' height='15' width='15'>";
        }
        else if (statusTable.rows[i].cells[0].innerHTML === "Nodes"){
            statusTable.rows[i].cells[1].innerHTML = "0";
        }
        else if (statusTable.rows[i].cells[0].innerHTML === "Discovery"){
            statusTable.rows[i].cells[1].innerHTML = "DISABLED <img src='images/ERROR.png' alt='NOK' height='15' width='15'>";
        }
    }
}
function nodesTableUpdate(jsonData){
    //update the table
    let nodesTable = document.getElementById("nodesTable");
    let map = jsonData["Mapping"];
    let empty = [];
    for (var i = 2; i < nodesTable.rows.length; i++) {
        if (i-2 < map.length) {
            entry = map[i - 2];
            if (entry["id"] in nodeData){
                if (nodeData[entry["id"]] !== undefined && nodeData[entry["id"]] != null) {
                    nodesTable.rows[i].cells[0].innerHTML = nodeData[entry["id"]]["node"]["name"];
                }else{
                    nodesTable.rows[i].cells[0].innerHTML = "&lt;&lt;name&gt;&gt;";
                }
            }else{
                nodesTable.rows[i].cells[0].innerHTML = "&lt;&lt;name&gt;&gt;";
            }
            nodesTable.rows[i].cells[1].innerHTML = entry["id"];
            nodesTable.rows[i].cells[2].innerHTML = entry["ip"];
            if (Date.now() - nodeLastPing[entry["id"]] > 2 * nodeUpdateInterval){
                nodesTable.rows[i].cells[3].innerHTML = "ONLINE <img src='images/WARNING.png' alt='OK' height='15' width='15'>";
            }else{
                nodesTable.rows[i].cells[3].innerHTML = "ONLINE <img src='images/OK.png' alt='OK' height='15' width='15'>";
            }
        }else{
            if (nodesTable.rows.length <= 3){
                nodesTable.rows[i].cells[0].innerHTML = "&lt;&lt;name&gt;&gt;";
                nodesTable.rows[i].cells[1].innerHTML = "&lt;&lt;Id&gt;&gt;";
                nodesTable.rows[i].cells[2].innerHTML = "Unknown";
                nodesTable.rows[i].cells[3].innerHTML = "Offline" + "<img src='images/ERROR.png' alt='NOK' height='15' width='15'>";
            }
            else{
                nodesTable.deleteRow(i);
                addRowHandlers()
                i--;
            }
        }
    }
    while (nodesTable.rows.length - 2 < map.length){
        //add rows
        var row = nodesTable.insertRow(nodesTable.rows.length);
        entry = map[nodesTable.rows.length - 3];
        row.insertCell(0).innerHTML = "&lt;&lt;name&gt;&gt;";
        row.insertCell(1).innerHTML = entry["id"];
        row.insertCell(2).innerHTML = entry["ip"];
        row.insertCell(3).innerHTML = "ONLINE <img src='images/OK.png' alt='OK' height='15' width='15'>";
        addRowHandlers()
    }
    /*if (map.length>0){
        for (var key in map){
            if (map.hasOwnProperty(key)){
                var row = nodesTable.insertRow(nodesTable.rows.length);
                var cell1 = row.insertCell(0);
                var cell2 = row.insertCell(1);
                var cell3 = row.insertCell(2);
                var cell4 = row.insertCell(3);
                cell1.innerHTML = "&#60;&#60;Id&#62;&#62;";
                cell2.innerHTML = key;
                cell3.innerHTML = map.get(key);
                cell4.innerHTML = "OFFLINE <img src='images/ERROR.png' alt='NOK' height='15' width='15'>";
            }
        }
    }*/
}
function nodesTableTimeout(){
    //update the table
    let nodesTable = document.getElementById("nodesTable");
    while (nodesTable.rows.length > 3) {
        nodesTable.deleteRow(3);
        if (nodesTable.rows.length === 3){
            addRowHandlers()
        }
    }
    nodesTable.rows[2].cells[0].innerHTML = "&lt;&lt;name&gt;&gt;";
    nodesTable.rows[2].cells[1].innerHTML = "&lt;&lt;Id&gt;&gt;";
    nodesTable.rows[2].cells[2].innerHTML = "Unknown";
    nodesTable.rows[2].cells[3].innerHTML = "Offline" + "<img src='images/ERROR.png' alt='NOK' height='15' width='15'>";
}
function clearDetails(){
    //clear table
    detailsTable.rows[1].cells[1].innerHTML = "N/A";
    detailsTable.rows[2].cells[1].innerHTML = "N/A";
    detailsTable.rows[3].cells[1].innerHTML = "N/A";
    detailsTable.rows[4].cells[1].innerHTML = "N/A";
    detailsTable.rows[5].cells[1].innerHTML = "N/A";
    detailsTable.rows[5].cells[2].innerHTML = "N/A";
    detailsTable.rows[6].cells[1].innerHTML = "N/A";
    detailsTable.rows[6].cells[2].innerHTML = "N/A";
}
function updateDetails(){
    //get table
    let detailsTable = document.getElementById("detailsTable");
    let nodeTable = document.getElementById("nodesTable");
    //look for the node index
    let index = -1;
    for (var i = 2; i < nodesTable.rows.length; i++){
        if (nodesTable.rows[i].cells[1].innerHTML === selectedNode){
            index = i;
            break;
        }
    }
    if (index === -1 || nodeData[selectedNode] === undefined ||nodeData[selectedNode] === null){
        clearDetails();
        return;
    }
    //update details table
    detailsTable.rows[1].cells[1].innerHTML = nodeData[selectedNode]["node"]["name"];
    detailsTable.rows[2].cells[1].innerHTML = nodesTable.rows[index].cells[3].innerHTML;
    detailsTable.rows[3].cells[1].innerHTML = nodeData[selectedNode]["node"]["id"];
    detailsTable.rows[4].cells[1].innerHTML = nodeData[selectedNode]["node"]["ip"];
    detailsTable.rows[5].cells[1].innerHTML = nodeData[selectedNode]["next"]["id"];
    detailsTable.rows[5].cells[2].innerHTML = nodeData[selectedNode]["next"]["ip"];
    detailsTable.rows[6].cells[1].innerHTML = nodeData[selectedNode]["prev"]["id"];
    detailsTable.rows[6].cells[2].innerHTML = nodeData[selectedNode]["prev"]["ip"];
}
function updateDetailsEvent(row,index){
    console.log(index)
    //get node id
    selectedNode = row.cells[1].innerHTML; //get node id
    //get details table
    let detailsTable = document.getElementById("detailsTable");
    let nodesTable = document.getElementById("nodesTable");
    if (!selectedNode in nodeData || nodeData[selectedNode] === undefined || nodeData[selectedNode] == null){
        clearDetails();
    }else {
        //update details table
        detailsTable.rows[1].cells[1].innerHTML = nodeData[selectedNode]["node"]["name"];
        detailsTable.rows[2].cells[1].innerHTML = nodesTable.rows[index + 2].cells[3].innerHTML;
        detailsTable.rows[3].cells[1].innerHTML = nodeData[selectedNode]["node"]["id"];
        detailsTable.rows[4].cells[1].innerHTML = nodeData[selectedNode]["node"]["ip"];
        detailsTable.rows[5].cells[1].innerHTML = nodeData[selectedNode]["next"]["id"];
        detailsTable.rows[5].cells[2].innerHTML = nodeData[selectedNode]["next"]["ip"];
        detailsTable.rows[6].cells[1].innerHTML = nodeData[selectedNode]["prev"]["id"];
        detailsTable.rows[6].cells[2].innerHTML = nodeData[selectedNode]["prev"]["ip"];
    }
}

function askIPofNameserver(){
    var valid = false;
    let ip = "";
    while (!valid){
        ip = prompt("Enter nameserver ip","localhost")
        if (ip == null || ip == ""){
            ip = "localhost";
            valid = true;
            break;
        }
        let bytes = ip.split(':')
        if (parseInt(bytes[0]) > 255 || parseInt(bytes[0]) < 0) {
            valid =false;
            alert("Invalid ip")
        } else if (parseInt(bytes[1]) > 255 || parseInt(bytes[1]) < 0) {
            valid = false;
            alert("Invalid ip")
        } else if (parseInt(bytes[2]) > 255 || parseInt(bytes[2]) < 0) {
            valid = false;
            alert("Invalid ip")
        } else if (parseInt(bytes[3]) > 255 || parseInt(bytes[3]) < 0) {
            valid = false;
            alert("Invalid ip")
        } else {
            valid = true;
        }
    }
    ipNS = ip;
    console.log("IP of nameserver: " + ipNS);
}

function addRowHandlers() {
    var table = document.getElementById("nodesTable");
    var rows = table.getElementsByTagName("tr");
    for (i = 2; i < rows.length; i++) {
        var currentRow = table.rows[i];
        var createClickHandler = function(row, index) {
            return function() {
                updateDetailsEvent(row,index);
            };
        };
        currentRow.onclick = createClickHandler(currentRow,i-2);
    }
}