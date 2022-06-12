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
    updateCards();
    newDataAvailable = false;
}

function updateCards(){
    const cardOverview = document.getElementById("card-overview");
    clearcontent("card-overview");

    if (nameServerData.Mapping === undefined){
        console.log("No data available");
        return;
    }
    if (nameServerData.Mapping.length == 0){
        //no data available
        return;
    }
    const activeNodes = sortByKey(nameServerData.Mapping,"id");
    console.log(activeNodes);
    for (let node of activeNodes) {
        try {
            let card = createCard(node.id);
            cardOverview.appendChild(card);
        } catch (e) {
        }
    }
}
function createCard(id){
    if (id == undefined) return;
    let div = document.createElement("div");
    div.className = "col-xl-3 col-lg-4 col-md-6 col-sm-12 col-12";
    let card = document.createElement("div");
    card.className = "card";
    let cardHeader = document.createElement("h1");
    cardHeader.className = "card-header card-dark";
    cardHeader.innerHTML = nodeData[id].node.name;
    let cardBody = document.createElement("div");
    cardBody.className = "scrollable-wrapper";
    let cardTable = document.createElement("table");
    cardTable.className = "table-dark table-scroll-fixed-headers";
    let cardTableHead = document.createElement("thead");
    let cardTableBody = document.createElement("tbody");
    let cardTableRow0 = document.createElement("tr");
    let cardTableRow1 = document.createElement("tr");
    let cardTableRow2 = document.createElement("tr");
    let cardTableRow3 = document.createElement("tr");
    let cardTableRow4 = document.createElement("tr");

    let cardTableCell0 = document.createElement("td");
    let cardTableCell1 = document.createElement("td");
    cardTableCell0.innerHTML = "Node name:";
    cardTableCell1.innerHTML = nodeData[id].node.name;
    cardTableRow0.appendChild(cardTableCell0);
    cardTableRow0.appendChild(cardTableCell1);

    cardTableCell0 = document.createElement("td");
    cardTableCell1 = document.createElement("td");
    cardTableCell0.innerHTML = "Node ID:";
    cardTableCell1.innerHTML = nodeData[id].node.id;
    cardTableRow1.appendChild(cardTableCell0);
    cardTableRow1.appendChild(cardTableCell1);

    cardTableCell0 = document.createElement("td");
    cardTableCell1 = document.createElement("td");
    cardTableCell0.innerHTML = "Node ip:";
    cardTableCell1.innerHTML = nodeData[id].node.ip;
    cardTableRow2.appendChild(cardTableCell0);
    cardTableRow2.appendChild(cardTableCell1);

    cardTableCell0 = document.createElement("td");
    cardTableCell1 = document.createElement("td");
    cardTableCell0.innerHTML = "Node status:";
    cardTableCell1.innerHTML = nodeLastPing[id]  + Math.max(parseInt(document.getElementById("nsRefreshRate").value)*2,10000) > Date.now() ?
        "online <img src='images/OK.png' alt='OK' height='15' width='15'>" :
        "offline <img src='images/ERROR.png' alt='ERROR' height='15' width='15'>";
    cardTableRow3.appendChild(cardTableCell0);
    cardTableRow3.appendChild(cardTableCell1);

    cardTableCell0 = document.createElement("td");
    cardTableCell1 = document.createElement("td");
    let terminateButton = document.createElement("button");
    terminateButton.className = "terminate";
    terminateButton.onclick = function(){
        terminateNode(id);
    };
    let terminateButtonIcon = document.createElement("img");
    terminateButtonIcon.src = "images/terminate.png";
    terminateButtonIcon.alt = "terminate";
    terminateButtonIcon.height = "30";
    terminateButtonIcon.width = "30";
    terminateButton.appendChild(terminateButtonIcon);

    let shutdownButton = document.createElement("button");
    shutdownButton.className = "shutdown";
    shutdownButton.onclick = function(){
        shutdownNode(id);
    };
    let shutdownButtonIcon = document.createElement("img");
    shutdownButtonIcon.src = "images/shutdown.png";
    shutdownButtonIcon.alt = "shutdown";
    shutdownButtonIcon.height = "30";
    shutdownButtonIcon.width = "30";
    shutdownButton.appendChild(shutdownButtonIcon);

    cardTableCell0.appendChild(terminateButton);
    cardTableCell1.appendChild(shutdownButton);
    cardTableRow4.appendChild(cardTableCell0);
    cardTableRow4.appendChild(cardTableCell1);

    cardTable.appendChild(cardTableRow0);
    cardTable.appendChild(cardTableRow1);
    cardTable.appendChild(cardTableRow2);
    cardTable.appendChild(cardTableRow3);
    cardTable.appendChild(cardTableRow4);
    cardBody.appendChild(cardTable);
    card.appendChild(cardHeader);
    card.appendChild(cardBody);
    div.appendChild(card);
    return div;
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

function clearcontent(elementID) {
    document.getElementById(elementID).innerHTML = "";
}
