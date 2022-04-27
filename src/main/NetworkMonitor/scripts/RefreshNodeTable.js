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
