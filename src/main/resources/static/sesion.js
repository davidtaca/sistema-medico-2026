/*
 * Control de sesión por inactividad (RN-GLOBAL-007). Se incluye en todas las
 * pantallas internas: si la persona no hace nada durante MINUTOS_INACTIVIDAD,
 * se cierra su sesión y se le pide iniciar sesión de nuevo.
 */
(function () {
    var MINUTOS_INACTIVIDAD = 30;
    var CLAVE = 'ultimaActividad';

    function hayCuenta() {
        try { return !!sessionStorage.getItem('usuarioActual'); } catch (e) { return false; }
    }

    function marcarActividad() {
        try { sessionStorage.setItem(CLAVE, String(Date.now())); } catch (e) { /* sin almacenamiento disponible */ }
    }

    function revisar() {
        if (!hayCuenta()) return;
        var ultima = parseInt(sessionStorage.getItem(CLAVE) || '0', 10);
        if (ultima && Date.now() - ultima > MINUTOS_INACTIVIDAD * 60 * 1000) {
            sessionStorage.removeItem('usuarioActual');
            sessionStorage.removeItem(CLAVE);
            alert('Su sesión ha expirado por inactividad. Por favor, inicie sesión nuevamente.');
            window.location.href = '/index.html';
        }
    }

    // Al cargar la pantalla: se revisa si la sesión ya había vencido y, si no, se marca como activa
    revisar();
    if (hayCuenta()) marcarActividad();

    // Cualquier uso de la pantalla cuenta como actividad (como máximo una vez cada 15 segundos)
    var ultimoRegistro = 0;
    ['click', 'keydown', 'mousemove', 'touchstart'].forEach(function (evento) {
        document.addEventListener(evento, function () {
            var ahora = Date.now();
            if (hayCuenta() && ahora - ultimoRegistro > 15000) {
                ultimoRegistro = ahora;
                marcarActividad();
            }
        }, true);
    });

    setInterval(revisar, 30000);
})();
