let selectedID = null;
setScreenRefreshRate(1000);



function setScreenRefreshRate(interval){
    setInterval(screenRefresh,interval);
}
function screenRefresh(){
    if (!newDataAvailable){ return; } //prevent unnecessary refreshes
    updateNameserverScreen();
    updateNodeTable();
    updateDetailsTable();
    newDataAvailable = false;
}
function updateNameserverScreen(){
    const table = document.getElementById("nameserverTable");

}
function updateNodeTable(){
    const table = document.getElementById("nodes-list");

}
function updateDetailsTable(){
    if (selectedID == null){ return; }
    const table = document.getElementById("node-details");
}