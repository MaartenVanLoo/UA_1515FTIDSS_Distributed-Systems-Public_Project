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
let dataRefreshInterval = 1000;



//init:
load();
setDataRefreshRate(dataRefreshInterval)
saveData();
screenRefresh(true);
try{
    loadSSHTunnelMap();
}catch(e){
}

function saveData() {
    sessionStorage.setItem("nameServerData", JSON.stringify(nameServerData));
    sessionStorage.setItem("nodeData", JSON.stringify(nodeData));
    sessionStorage.setItem("nodeLastPing", JSON.stringify(nodeLastPing));
    sessionStorage.setItem("newDataAvailable", JSON.stringify(newDataAvailable));
}
function saveSettings(){
    sessionStorage.setItem("ipNS", JSON.stringify(ipNS));
    sessionStorage.setItem("ipPort", ipPort);
    sessionStorage.setItem("dataRefreshInterval", dataRefreshInterval);
    sessionStorage.setItem("sshTunnel", document.getElementById("enable-ssh-tunnel").checked);
}
function load(){
    //load data
    nameServerData ="nameServerData" in sessionStorage ? JSON.parse(sessionStorage.getItem("nameServerData")) : {};
    nodeData = "nodeData" in sessionStorage? JSON.parse(sessionStorage.getItem("nodeData")):{};
    nodeLastPing = "nodeLastPing" in sessionStorage? JSON.parse(sessionStorage.getItem("nodeLastPing")):{};
    newDataAvailable = "newDataAvailable" in sessionStorage? JSON.parse(sessionStorage.getItem("newDataAvailable")):true;

    //load settings
    ipNS = "ipNS" in sessionStorage? JSON.parse(sessionStorage.getItem("ipNS")):"localhost";
    ipPort = "ipPort" in sessionStorage? parseInt(sessionStorage.getItem("ipPort")):8081;
    dataRefreshInterval = "dataRefreshInterval" in sessionStorage? sessionStorage.getItem("dataRefreshInterval"):1000;


    document.getElementById("ns-ip").value = ipNS === "localhost" ? "" : ipNS;
    document.getElementById("ns-port").value = ipPort === 8081? "": parseInt(ipPort);

    document.getElementById("nsRefreshRate").value = parseInt(dataRefreshInterval);
    document.getElementById("nsRefreshRateLabel").innerHTML = numberWithSpaces(dataRefreshInterval) +" ms";
    document.getElementById("enable-ssh-tunnel").checked = "sshTunnel" in sessionStorage? parseBoolean(sessionStorage.getItem("sshTunnel")):true;
}
function parseBoolean(str){
    return str === "true";
}
function setDataRefreshRate(interval){
    if (dataIntervalId != null) clearInterval(dataIntervalId);
    dataIntervalId = setInterval(updateData,interval);
    dataRefreshInterval = interval;
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
async function sendTerminateNode(url = ''){
    try{
        const response = await fetch(url, {
            method: 'DELETE', // *GET, POST, PUT, DELETE, etc.

        body: JSON.stringify({method:'terminate'}),
        })
        return await response.json(); // parses JSON response into native JavaScript object
    }
    catch(e){
        //console.log(e);
        return undefined;
    }
}
async function sendShutdownNode(url = ''){
    try{
        const response = await fetch(url, {
            method: 'DELETE', // *GET, POST, PUT, DELETE, etc.
            body: JSON.stringify({method:'shutdown'}),
        })
        return await response.json(); // parses JSON response into native JavaScript object
    }
    catch(e){
        //console.log(e);
        return undefined;
    }
}

async function terminateNode(id){
    if (nodeData[id].node.ip == undefined) return;
    let ip = nodeData[id].node.ip;
    let port = translateSSHTunnelPort(ip,ipPort);
    ip = translateSSHTunnelIP(ip);
    let url = "http://"+ip+":"+port+"/node";
    await sendTerminateNode(url);
}

async function shutdownNode(id){
    if (nodeData[id].node.ip == undefined) return;
    let ip = nodeData[id].node.ip;
    let port = translateSSHTunnelPort(ip,ipPort);
    ip = translateSSHTunnelIP(ip);
    let url = "http://"+ip+":"+port+"/node";
    await sendShutdownNode(url);
}

const getNodeData = async (ips) =>{
    if (nodeRequest > 2* ips.length){
        return null;
    }
    if (ips.length == 0){
        return null;
    }
    const nodes = ips.map(async ip =>{
        if (ip == "Unknown") return undefined;
        nodeRequest++;
        let port = translateSSHTunnelPort(ip,ipPort);
        ip = translateSSHTunnelIP(ip);
        const data = await GetData("http://"+ ip +":"+port+"/node");
        nodeRequest--;
        return data;
    })
    return Promise.all(nodes);
}
const getNameserverData = async (ip) =>{
    if (nameserverRequests > 0) return undefined;
    nameserverRequests++;
    let port = translateSSHTunnelPort(ip,ipPort);
    ip = translateSSHTunnelIP(ip);
    const data = await GetData("http://"+ ip +":"+port + "/ns");
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
                saveData();
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
        .then(() => {saveData();})
        .catch((error) => {});
}
async function updateNodes(){
    if (nameServerData["Mapping"] === undefined || nameServerData["Mapping"] == null) return;

    let ips = nameServerData["Mapping"].map((e) => {return e["ip"]});
    getNodeData(ips)
        .then(data => {
            if (data === undefined || data == null) return;
            data.map( (e) => {
                if (e === undefined || e == null) return;
                if (e["node"]["id"] in nodeData){
                    nodeData[e["node"]["id"]]= e;
                    nodeLastPing[e["node"]["id"]] = Date.now();
                }
            });
        })
        .then(() => {saveData();})
        .then(() =>{newDataAvailable = true})
}

function nodeOnline(id){
    if (id == undefined || id == null) return false;
    if (nodeData[id] === undefined || nodeData[id] == null) return false;
    return nodeLastPing[id]  + Math.max(parseInt(document.getElementById("nsRefreshRate").value)*2,10000) > Date.now()
}

//ssh tunnel
function loadSSHTunnelMap(){

}
//check if tunnel checkbox is checekd
function checkSSHTunnel(){
    if (document.getElementById("enable-ssh-tunnel").checked){
        return true;
    }
    else{
        return false;
    }
}
function translateSSHTunnelIP(ip){
    if (checkSSHTunnel()){
        if (ip in sshTunnelMap){
            return sshTunnelMap[ip].ip;
        }
        else{
            return ip;
        }
    }
    else{
        return ip;
    }
}
function translateSSHTunnelPort(ip,port){
    if (checkSSHTunnel()){
        if (ip in sshTunnelMap){
            return sshTunnelMap[ip].port;
        }
        else{
            return port;
        }
    }
    else{
        return port;
    }
}
