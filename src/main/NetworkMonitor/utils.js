function toggle_options(){
    document.getElementById("dropdown-options").classList.toggle("show");
}
function numberWithSpaces(x) {
    return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, " ");
}