let selectedID = null;
setDataRefreshRate(1000);
setScreenRefreshRate(1000);



function setScreenRefreshRate(interval){
    setInterval(screenRefresh,interval);
}
function screenRefresh(){
    updateNameserverScreen();
    updateNodeTable();
}
function updateNameserverScreen(){
    const table = document.getElementById("nameserverTable");

}
function updateNodeTable(){
    const table = document.getElementById("nodeTable");

}
function updateDetailsTable(){

}