let selectedID = null;
let screenIntervalId = null;
setScreenRefreshRate(1000);




function setScreenRefreshRate(interval){
    if (screenIntervalId != null) clearInterval(screenIntervalId);
    screenIntervalId = setInterval(screenRefresh,interval);
}
function screenRefresh(force){
    //only true when both force is false and no new data is available
    force = force===undefined?false:force;
    if (!newDataAvailable && !force){ return; } //prevent unnecessary refreshes

    newDataAvailable = false;
    updateNameserverScreen();
    updateNodeTable();
    updateDetailsTable();
}

function updateNameserverScreen(){
    const table = document.getElementById("nameserverTable");
    const status = document.getElementById("ns-status");
    const nodes = document.getElementById("ns-nodes");
    const util_discovery = document.getElementById("ns-discovery");

    //update status
    if (nameServerData.hasOwnProperty("Status") && nameServerData.Status ==="running"){
        status.innerHTML =  "online <img src='images/OK.png' alt='OK' height='15' width='15'>"
    }
    else{
        status.innerHTML =  "offline <img src='images/ERROR.png' alt='Error' height='15' width='15'>"
    }

    //update node count
    if (nameServerData.hasOwnProperty("Nodes")){
        nodes.innerHTML = nameServerData.Nodes;
    }
    else{
        nodes.innerHTML = "0";
    }

    //update utilities:
    if (!nameServerData.hasOwnProperty("Utilities")) {
        util_discovery.innerHTML = "disabled <img src='images/ERROR.png' alt='Error' height='15' width='15'>";
        return
    };
    //update discovery
    if (nameServerData.Utilities.hasOwnProperty("Discovery") && nameServerData.Utilities.Discovery ==="enabled"){
        util_discovery.innerHTML = nameServerData.Utilities.Discovery + " <img src='images/OK.png' alt='OK' height='15' width='15'>"
    }
    else{
        util_discovery.innerHTML = "disabled <img src='images/ERROR.png' alt='Error' height='15' width='15'>";
    }

}
function updateNodeTable(){
    let table = document.getElementById("nodes-list");

    //get active nodes
    if (!nameServerData.hasOwnProperty("Mapping")) {
        clearNodeTable()
        return;
    }
    const activeNodes = sortByKey(nameServerData.Mapping,"id");
    console.log(activeNodes);

    //update table
    clearNodeTable();
    let nodeIndex = 0;
    for (let node of activeNodes){
        appendNodeTable(node,table);
    }
}
function appendNodeTable(node, table){
    row = table.insertRow()
    row.insertCell().innerHTML = nodeData[node.id] == null? node.id : nodeData[node.id].node.name;
    row.insertCell().innerHTML = node.id;
    row.insertCell().innerHTML = node.ip;
    row.insertCell().innerHTML = nodeLastPing[node.id]  + Math.max(parseInt(document.getElementById("nsRefreshRate").value)*2,10000) > Date.now() ?
        "online <img src='images/OK.png' alt='OK' height='15' width='15'>" :
        "offline <img src='images/ERROR.png' alt='ERROR' height='15' width='15'>";
    addRowHandler(row,node);
}
function insertNodeTable(node, table, index){
    row = table.insertRow(index)
    row.insertCell().innerHTML = nodeData[node.id] == null? node.id : nodeData[node.id].name;
    row.insertCell().innerHTML = node.id;
    row.insertCell().innerHTML = node.ip;
    row.insertCell().innerHTML = nodeLastPing[node.id] + Math.max(parseInt(document.getElementById("nsRefreshRate").value)*2,10000) > Date.now() ?
        "online <img src='images/OK.png' alt='OK' height='15' width='15'>" :
        "offline <img src='images/ERROR.png' alt='ERROR' height='15'>";
    addRowHandler(row,node);
}

function clearNodeTable(){
    const table = document.getElementById("nodes-list");
    while (table.rows.length > 0) removeRow(table);
}
function updateDetailsTable(){
    if (selectedID == null){ return; }
    const table = document.getElementById("node-details");
    console.log(selectedID);

    //update name:
    if (nodeData[selectedID] == null) {
        document.getElementById("node-details-name").innerHTML = "please select a node";
        document.getElementById("node-details-status").innerHTML = "-";
        document.getElementById("node-details-id").innerHTML = "-" ;
        document.getElementById("node-details-ip").innerHTML = "-" ;
        document.getElementById("node-details-next-id").innerHTML = "-";
        document.getElementById("node-details-next-ip").innerHTML = "-";
        document.getElementById("node-details-prev-id").innerHTML = "-";
        document.getElementById("node-details-prev-ip").innerHTML = "-";
        document.getElementByid("local").innerHTML = "-";
        document.getElementById("replica").innerHTML = "-";
    }else{
        document.getElementById("node-details-name").innerHTML = nodeData[selectedID].node.name;
        document.getElementById("node-details-status").innerHTML = nodeLastPing[selectedID]  + Math.max(parseInt(document.getElementById("nsRefreshRate").value)*2,10000) > Date.now() ?
            "online <img src='images/OK.png' alt='OK' height='15' width='15'>" :
            "offline <img src='images/ERROR.png' alt='ERROR' height='15' width='15'>";
        document.getElementById("node-details-id").innerHTML = nodeData[selectedID].node.id;
        document.getElementById("node-details-ip").innerHTML = nodeData[selectedID].node.ip;
        document.getElementById("node-details-next-id").innerHTML = nodeData[selectedID].next.id;
        document.getElementById("node-details-next-ip").innerHTML = nodeData[selectedID].next.ip;
        document.getElementById("node-details-prev-id").innerHTML = nodeData[selectedID].prev.id;
        document.getElementById("node-details-prev-ip").innerHTML = nodeData[selectedID].prev.ip;
        document.getElementById("local").innerHTML = nodeData[selectedID].local;
        docuemnt.getEleemntById("replica").innerHTML = nodeData[selectedID].replica;
    }

}


//------------------------------------------------------
//helper functions
//------------------------------------------------------
function sortByKey(array,key){
    return array.sort(function(a,b){
        var x = a[key]; var y = b[key];
        return ((x < y) ? -1 : ((x > y) ? 1 : 0));
    });
}
function removeRow(table, index){
    table.deleteRow(index);
}
function addRowHandler(row,node) {
    var createClickHandler = function(node) {
        return function() {
            selectedID = node.id;
            updateDetailsTable();
        };
    };
    row.onclick = createClickHandler(node);

}
