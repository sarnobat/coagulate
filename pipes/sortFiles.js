<script>
var urlBase = "http://netgear.rohidekar.com:44451/cmsfs/";
function moveFile(filePath, destDirFull) {
	var destinationDirSimpleName = destDirFull.substring(destDirFull.lastIndexOf('/') + 1);

        $.getJSON(urlBase + "/move?filePath="+encodeURIComponent(filePath) 
			+ "&destinationDirSimpleName=" 
			+ encodeURIComponent(destinationDirSimpleName),function(response){

                //removeImageElement(filePath);
		alert("Success. Now remove the element");
                // TODO: Also remove from pairs global variable so it doesn't keep coming back when we shuffle
        });
}
</script>
