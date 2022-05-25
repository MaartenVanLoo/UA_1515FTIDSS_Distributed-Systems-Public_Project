let screenIntervalId = null;
let sortCol = 0;
let sortDir = "asc";
setScreenRefreshRate(1000);



function setScreenRefreshRate(interval){
    if (screenIntervalId != null) clearInterval(screenIntervalId);
    screenIntervalId = setInterval(screenRefresh,interval);
}
function screenRefresh(force){
    //only true when both force is false and no new data is available
    force = force===undefined?false:force;
    if (!newDataAvailable && !force){ return; } //prevent unnecessary refreshes
    updateFileTable();
    newDataAvailable = false;
}

function updateFileTable(){
    let table = document.getElementById("file-table-body");
    //get all files
    //nameServerData["Mapping"].map((e) => {return e["ip"]});
    let fileList = {};
    for (const node in nodeData){
        //loop over local files
        for (const file in nodeData[node].local){
            let key = nodeData[node].local[file];
            if (fileList[key] === undefined){
                fileList[key] = {};
            }
            fileList[key].local = nodeData[node].node.name;
        }
        //loop over replicated files
        for (const file in nodeData[node].replica){
            let key = nodeData[node].replica[file];
            if (fileList[key] === undefined){
                fileList[key] = {};
            }
            fileList[key].replica = nodeData[node].node.name;
        }
    }

    //clear table
    table.innerHTML = "";

    //print date in table
    Object.keys(fileList).forEach((key) => {
        console.log(key);
        console.log(bytesToShort(toBytes(MD5(key))));
        let row = document.createElement("tr");
        let name = document.createElement("td");
        let local = document.createElement("td");
        let replica = document.createElement("td");
        let status = document.createElement("td");
        name.innerHTML = key;
        local.innerHTML = fileList[key].local === undefined? "":fileList[key].local;
        replica.innerHTML = fileList[key].replica === undefined? "":fileList[key].replica;
        let ok = fileList[key].local !== undefined && fileList[key].replica !== undefined && fileList[key].local !== fileList[key].replica;
        status.innerHTML = ok?"<img src='images/OK.png' alt='OK' height='15' width='15'>" :
                            "<img src='images/ERROR.png' alt='ERROR' height='15' width='15'>";

        local.style.textAlign = "center";
        replica.style.textAlign = "center";
        status.style.textAlign = "center";

        row.appendChild(name);
        row.appendChild(local);
        row.appendChild(replica);
        row.appendChild(status);
        table.appendChild(row);
    });
    sortTable();
    return;
}

function sortTable(n) {
    if (n === undefined) n = sortCol;
    else if (n === sortCol) {
        if (sortDir === "asc") sortDir = "desc";
        else sortDir = "asc";
    }
    sortCol = n;

    var table, rows, switching, i, x, y, shouldSwitch, dir, switchcount = 0;
    table = document.getElementById("file-table");
    switching = true;
    //Set the sorting direction to requested direction:
    dir = sortDir;
    /*Make a loop that will continue until
    no switching has been done:*/
    while (switching) {
        //start by saying: no switching is done:
        switching = false;
        rows = table.rows;
        /*Loop through all table rows (except the
        first, which contains table headers):*/
        for (i = 1; i < (rows.length - 1); i++) {
            //start by saying there should be no switching:
            shouldSwitch = false;
            /*Get the two elements you want to compare,
            one from current row and one from the next:*/
            x = rows[i].getElementsByTagName("TD")[n];
            y = rows[i + 1].getElementsByTagName("TD")[n];
            /*check if the two rows should switch place,
            based on the direction, asc or desc:*/
            if (dir == "asc") {
                if (x.innerHTML.toLowerCase() > y.innerHTML.toLowerCase()) {
                    //if so, mark as a switch and break the loop:
                    shouldSwitch= true;
                    break;
                }
            } else if (dir == "desc") {
                if (x.innerHTML.toLowerCase() < y.innerHTML.toLowerCase()) {
                    //if so, mark as a switch and break the loop:
                    shouldSwitch = true;
                    break;
                }
            }
        }
        if (shouldSwitch) {
            /*If a switch has been marked, make the switch
            and mark that a switch has been done:*/
            rows[i].parentNode.insertBefore(rows[i + 1], rows[i]);
            switching = true;
            //Each time a switch is done, increase this count by 1:
            switchcount ++;
        }
    }
}