$(document).ready(function(){

    $('#allbtn').click(function () {
        $('#allbtn').addClass("active");
        $('#allbizbtn').removeClass("active");
        $('#zpubtn').removeClass("active");
        $('.zakupki').show();
        $('.allbiz').show();
    });
    $('#allbizbtn').click(function () {
        $('#allbizbtn').addClass("active");
        $('#allbtn').removeClass("active");
        $('#zpubtn').removeClass("active");
        $('.zakupki').hide();
        $('.allbiz').show();
    });
    $('#zpubtn').click(function () {
        $('#zpubtn').addClass("active");
        $('#allbtn').removeClass("active");
        $('#allbizbtn').removeClass("active");
        $('.zakupki').show();
        $('.allbiz').hide();
    });

});
