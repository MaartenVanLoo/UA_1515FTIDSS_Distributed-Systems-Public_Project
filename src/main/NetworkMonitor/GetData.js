//All data get methods
//limit pending requests
let nodeRequest = 0;
let nameserverRequests = 0;

//global data
let nameServerData = {};    //nameserver data, including node mapping
let nodeData = {};          //node data, fetched from the nodes
let nodeLastPing = {};      //last answer from the nodes
let newDataAvailable = false;
let ipNS = "localhost" 

setDataRefreshRate(1000)


function setDataRefreshRate(interval){
    setInterval(updateData,interval);
}
function setNameServerIp(ip){
    ipNS = ip;
}

async function GetData(url = '') {
    // Default options are marked with *
    try {
        const response = await fetch(url, {
            method: 'GET', // *GET, POST, PUT, DELETE, etc.
        })
        let body = await response.json(); // parses JSON response into native JavaScript object
        return body;
    }
    catch(e){
        //console.log(e);
        return undefined;
    }
}
async function DeleteData(url = '') {
    // Default options are marked with *
    try {
        const response = await fetch(url, {
            method: 'DELETE', // *GET, POST, PUT, DELETE, etc.
        })
        return await response.json(); // parses JSON response into native JavaScript object
    }
    catch(e){
        //console.log(e);
        return undefined;
    }
}
async function DeleteData(url = '') {
    // Default options are marked with *
    try {
        const response = await fetch(url, {
            method: 'DELETE', // *GET, POST, PUT, DELETE, etc.
        })
        return await response.status; // parses JSON response into native JavaScript object
    }
    catch(e){
        //console.log(e);
        return undefined;
    }
}
const getNodeData = async (ips) =>{
    const nodes = ips.map(async ip =>{
        if (nodeRequest > 2* ips.length){
            return null;
        }
        if (ip == "Unknown") return undefined;
        nodeRequest++;
        const data = await GetData("http://"+ ip +":8081/node");
        nodeRequest--;
        return data;
    })
    return Promise.all(nodes);
}
const getNameserverData = async (ip) =>{
    if (nameserverRequests > 0) return undefined;
    nameserverRequests++;
    const data = await GetData("http://"+ ip +":8081/ns");
    nameserverRequests--;
    return data;
}

async function updateData(){
    updateNS();
    updateNodes();
}

async function updateNS(){
    //async function => no awaiting, just continues!
    getNameserverData(ipNS)
        .then(data => {
            if (data !== undefined){
                nameServerData = data;
            }
            else{
                throw "No data from nameserver";
            }
        })
        .then(()=> {
            //update the nodeData map
            //remove keys not present in current in nameserver data
            let nodes = nameServerData["Mapping"].map((e) => {return e["id"]});
            for (let node of Object.keys(nodeData)) {
                if (!nodes.includes(node)) {
                    delete nodeData[node];
                }
            }
            //add new keys
            for (let node of nodes) {
                if (!Object.keys(nodeData).includes(node)) {
                    nodeData[node] = null;
                    nodeLastPing[node] = Date.now();
                }
            }
        })
        .then(() =>{newDataAvailable = true})
        .catch((error) => {});
}
async function updateNodes(){
    if (nameServerData["Mapping"] === undefined || nameServerData["Mapping"] == null) return;

    let ips = nameServerData["Mapping"].map((e) => {return e["ip"]});
    getNodeData(ips)
        .then(data => {
            data.map( (e) => {
                if (e === undefined || e == null) return
                if (e["node"]["id"] in nodeData){
                    nodeData[e["node"]["id"]]= e;
                    nodeLastPing[e["node"]["id"]] = Date.now();
                }
            });
        })
        .then(() =>{newDataAvailable = true})
}