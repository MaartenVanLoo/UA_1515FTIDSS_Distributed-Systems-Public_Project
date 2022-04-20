//All data get methods
//limit pending requests
let nodeRequest = 0;
let nameserverRequests = 0;

//global data
let nameServerData = {};    //nameserver data, including node mapping
let nodeData = {};          //node data, fetched from the nodes
let nodeLastPing = {};      //last answer from the nodes
let newDataAvailable = true; //set true to instantly trigger update of screen

let sshTunnelMap = {
    "192.168.48.2":
    {"host-name":"host0",
        "ip":"127.0.0.1",
        "port":8081	},
    "192.168.48.3":
    {"host-name":"host1",
        "ip":"127.0.0.1",
        "port":8082	},
    "192.168.48.5":
    {"host-name":"host2",
        "ip":"127.0.0.1",
        "port":8083	},
    "192.168.48.4":
    {"host-name":"host3",
        "ip":"127.0.0.1",
        "port":8084	},
    "192.168.48.6":
    {"host-name":"host4",
        "ip":"127.0.0.1",
        "port":8085	}
};

let ipNS = "localhost" 
let ipPort = 8081;
let dataIntervalId = null;
setDataRefreshRate(1000)

//init:
try{
    loadSSHTunnelMap();
}catch(e){
}

function setDataRefreshRate(interval){
    if (dataIntervalId != null) clearInterval(dataIntervalId);
    dataIntervalId = setInterval(updateData,interval);
}
function setNameServerIp(ip){
    if (ip === "" || ip === undefined) ip = "localhost";
    ipNS = ip;
}
function setNameServerPort(port){
    if (port === "" || port === undefined) port = 8081;
    ipPort = port;
}
async function GetData(url = '') {
    // Default options are marked with *
    try {
        const response = await fetch(url, {
            method: 'GET', // *GET, POST, PUT, DELETE, etc.
			credentials: 'omit', // include, *same-origin, omit
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
        //ip = translateSSHTunnel(ip);
        const data = await GetData("http://"+ ip +":8081/node");
        nodeRequest--;
        return data;
    })
    return Promise.all(nodes);
}
const getNameserverData = async (ip) =>{
    if (nameserverRequests > 0) return undefined;
    nameserverRequests++;
    //ip = translateSSHTunnel(ip);
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
                nameServerData = {};
                nodeData = {};
                nodeLastPing = {};
                newDataAvailable = true;
                throw "No data from nameserver";
            }
        })
        .then(()=> {
            //update the nodeData map
            //remove keys not present in current in nameserver data
            let nodes = nameServerData["Mapping"].map((e) => {return e["id"]});
            for (let node of Object.keys(nodeData)) {
                if (!nodes.includes(parseInt(node))) {
                    delete nodeData[node];
                    delete nodeLastPing[node];
                }
            }
            //add new keys
            for (let node of nodes) {
                if (!Object.keys(nodeData).includes(String(node))) {
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
                if (e === undefined || e == null) return;
                if (e["node"]["id"] in nodeData){
                    nodeData[e["node"]["id"]]= e;
                    nodeLastPing[e["node"]["id"]] = Date.now();
                }
            });
        })
        .then(() =>{newDataAvailable = true})
}


//ssh tunnel
async function loadSSHTunnelMap(){

}
//check if tunnel checkbox is checekd
function checkSSHTunnel(){
    if (document.getElementById("loadSSHTunnelMap").checked){
        return true;
    }
    else{
        return false;
    }
}
function translateSSHTunnel(ip){
    if (checkSSHTunnel()){
        if (ip in sshTunnelMap){
            return sshTunnelMap[ip];
        }
        else{
            return ip;
        }
    }
    else{
        return ip;
    }
}