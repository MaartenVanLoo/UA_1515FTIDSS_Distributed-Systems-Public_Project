let angles = {};
let canvas = document.getElementById("network-map");
let screenIntervalId = null;
setScreenRefreshRate(1000);
canvasInit();
function setScreenRefreshRate(interval){
    if (screenIntervalId != null) clearInterval(screenIntervalId);
    screenIntervalId = setInterval(screenRefresh,interval);
}
function screenRefresh(force){
    //only true when both force is false and no new data is available
    force = force===undefined?false:force;
    if (!newDataAvailable && !force){ return; } //prevent unnecessary refreshes
    newDataAvailable = false
	draw();
}
function canvasInit(){
    const context = canvas.getContext('2d');
    context.translate(canvas.width/2, canvas.height/2);
}
function draw(){
    const context = canvas.getContext('2d');
    computeCoordinates(nodeData);
    drawObjects(nodeData);
}
function clear(context){
    context.clearRect(-canvas.width/2, -canvas.height/2, canvas.width, canvas.height);
}
function toRadians(angle){
    return angle * (Math.PI / 180);
}
function computeCoordinates(objects){
    // evenly space the objects on a circle
    const radius = Math.min(canvas.width,canvas.height) * 1 / 3;
    const nodeCount = Object.keys(objects).length === undefined?0:Object.keys(objects).length;
    if (nodeCount === 0) return;
    if (nodeCount === 1)  {angles[Object.keys(objects)[0]] = {x: -radius, y: -radius}; return;}
    if (nodeCount === 2)  {
        angles[Object.keys(objects)[0]] = {x: radius * Math.cos(toRadians(50)), y: radius * Math.sin(toRadians(50))};
        angles[Object.keys(objects)[1]] = {x: radius * Math.cos(toRadians(130)), y: radius * Math.sin(toRadians(130))};
        return;
    }
    const angleStep = 2 * Math.PI / nodeCount;
    let angle = 0;
    for(let i = 0; i < nodeCount; i++){
        const x = radius * Math.cos(angle);
        const y = radius * Math.sin(angle);
        angles[Object.keys(objects)[i]] = {x: x, y: y};
        angle += angleStep;
    }
}
function drawObjects(objects){
    //draw a circle on the location of the objects
    const context = canvas.getContext('2d');
    clear(context);
    //"draw circle" = 2/3 of the smallest dimension
    // circumference = 2 * pi * 2/3 of the smallest dimension
    // max node circle = circumference/4 / number of nodes
    let nodeCount = Object.keys(objects).length === undefined?0:Object.keys(objects).length;
    if (nodeCount === 0) return;
    let maxNodeRadius = Math.min(canvas.width,canvas.height)/7;
    let nodeCircleRadius = Math.min(canvas.width,canvas.height) * 2/3 *3.14 / (nodeCount==0?1:nodeCount) / 5;
    nodeCircleRadius = Math.min(nodeCircleRadius,maxNodeRadius);
	for (let i = 0; i < nodeCount;i++){
        const nodeId = Object.keys(objects)[i]
        nodeOnline(nodeId)?context.fillStyle = 'green':context.fillStyle = 'red';
        context.beginPath();
        context.arc(angles[nodeId].x, angles[nodeId].y, nodeCircleRadius, 0, 2 * Math.PI);
        context.fill();
    }
    context.fillStyle = 'green';
    context.beginPath();
    context.arc(0, 0, Math.min(canvas.width,canvas.height)/8, 0, 2 * Math.PI);
    context.fill();

    //draw  line in between the objects
    for(let i = 0; i < nodeCount; i++){
        if (Object.keys(objects)[i] === undefined) continue;

        id = Object.keys(objects)[i];
        if(objects[id] === undefined || objects[id] == null) continue;
        //get prev and next node
        const prev = objects[id].prev;
        const next = objects[id].next;
        if (prev != undefined){
            drawArc(angles[id],angles[prev.id],10,context);
        }
        if (next != undefined){
            drawArc(angles[id],angles[next.id],-10,context);
        }
    }
}
function drawArc(from,to,radius){
    const context = canvas.getContext('2d');
    var center = {
        x: (from.x + to.x) / 2,
        y: (from.y + to.y) / 2
    };

    var rotation = Math.atan2(to.y - from.y,to.x - from.x);
    if (radius < 0){
        radius = -radius;
    }
    var startAngle = -0;
    var endAngle = Math.PI;
    var longAxis = Math.sqrt(Math.pow(to.x - from.x, 2) + Math.pow(to.y - from.y, 2));
    context.beginPath();
    context.ellipse(center.x,center.y,longAxis/2,radius,rotation,startAngle,endAngle,false);
    context.stroke();
}