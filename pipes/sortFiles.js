var urlBase = "http://netgear.rohidekar.com:44451/cmsfs/";
function moveFile(filePath, destinationDirSimpleName,buttonElement) {
console.debug(buttonElement);
        $.getJSON(urlBase + "/move?filePath="+encodeURIComponent(filePath) 
			+ "&destinationDirSimpleName=" 
			+ encodeURIComponent(destinationDirSimpleName),function(response){

		//alert("Success. Now remove the element");
		buttonElement.parentElement.remove()
        });
}
