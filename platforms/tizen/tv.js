(function () {
  'use strict';

  var DEVICE_LIST = [
    { id: 'lights', title: 'Iluminación', icon: 'L', members: ['light_front', 'light_center', 'light_back'] },
    { id: 'fan_main', title: 'Ventilador', icon: 'V' },
    { id: 'ac_main', title: 'Aire acondicionado', icon: 'A' },
    { id: 'projector', title: 'Proyector', icon: 'P' },
    { id: 'security_alarm', title: 'Alarma', icon: '!' }
  ];
  var SCENE_ON = ['light_front', 'light_center', 'light_back', 'fan_main', 'ac_main', 'projector'];
  var state = { devices: {}, sensors: {} };
  var config = loadConfig();
  var activePage = 'overview';
  var busy = false;

  function byId(id) { return document.getElementById(id); }
  function loadConfig() {
    var saved = {};
    try { saved = JSON.parse(localStorage.getItem('smartclass.gateway') || '{}'); } catch (ignore) {}
    if (!saved.url && location.protocol.indexOf('http') === 0) saved.url = location.origin;
    saved.classroom = saved.classroom || 'aula-204';
    saved.token = saved.token || '';
    return saved;
  }
  function saveConfig() {
    config.url = byId('gateway-url').value.replace(/\/+$/, '');
    config.token = byId('client-token').value;
    config.classroom = byId('classroom-id').value || 'aula-204';
    try { localStorage.setItem('smartclass.gateway', JSON.stringify(config)); } catch (ignore) {}
  }
  function setStatus(message) { byId('status-message').textContent = message; }
  function setError(message) {
    var node = byId('error-banner');
    node.textContent = message;
    node.className = message ? 'error-banner' : 'error-banner hidden';
  }
  function request(path, method, body, done) {
    if (!config.url) { done(new Error('Configura la URL del gateway.')); return; }
    var xhr = new XMLHttpRequest();
    xhr.open(method || 'GET', config.url + path, true);
    xhr.timeout = 7000;
    xhr.setRequestHeader('Accept', 'application/json');
    if (config.token) xhr.setRequestHeader('Authorization', 'Bearer ' + config.token);
    if (body) xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.onreadystatechange = function () {
      if (xhr.readyState !== 4) return;
      if (xhr.status >= 200 && xhr.status < 300) {
        var data = {};
        try { data = xhr.responseText ? JSON.parse(xhr.responseText) : {}; } catch (error) { done(new Error('El gateway respondió datos inválidos.')); return; }
        done(null, data);
      } else if (xhr.status === 401) done(new Error('Clave de acceso incorrecta. Revisa la configuración Gateway.'));
      else if (xhr.status) done(new Error('Gateway respondió HTTP ' + xhr.status + '.'));
      else done(new Error('No se pudo conectar. Revisa URL, Wi-Fi y gateway.'));
    };
    xhr.ontimeout = function () { done(new Error('El gateway tardó demasiado en responder.')); };
    xhr.onerror = function () { done(new Error('Error de red. TV y gateway deben estar en la misma red.')); };
    xhr.send(body ? JSON.stringify(body) : null);
  }
  function statePath() { return '/api/v1/classrooms/' + encodeURIComponent(config.classroom) + '/state'; }
  function refresh(silent) {
    if (busy || !config.url) return;
    request(statePath(), 'GET', null, function (error, result) {
      if (error) {
        setConnected(false);
        if (!silent) setError(error.message);
        setStatus('Sin conexión · ' + error.message);
        return;
      }
      state = result || { devices: {}, sensors: {} };
      state.devices = state.devices || {};
      state.sensors = state.sensors || {};
      setConnected(true);
      setError('');
      setStatus('Estado actualizado · ' + new Date().toLocaleTimeString());
      render();
    });
  }
  function setConnected(connected) {
    var pill = byId('connection-pill');
    pill.className = 'connection-pill ' + (connected ? 'online' : 'offline');
    pill.getElementsByTagName('b')[0].textContent = connected ? 'Conectado' : 'Sin conectar';
  }
  function isOn(id) { return !!(state.devices[id] && state.devices[id].state === 'on'); }
  function isConnected(id) { return !!(state.devices[id] && state.devices[id].connected); }
  function labelFor(item) {
    var ids = item.members || [item.id];
    var on = 0;
    var available = 0;
    for (var i = 0; i < ids.length; i += 1) {
      if (isConnected(ids[i])) available += 1;
      if (isOn(ids[i])) on += 1;
    }
    if (!available) return 'Sin conexión';
    if (item.members) return on + ' de ' + ids.length + ' zonas encendidas';
    if (item.id === 'security_alarm') return isOn(item.id) ? 'Activa' : 'Desactivada';
    return isOn(item.id) ? 'Encendido' : 'Apagado';
  }
  function makeDeviceTile(item) {
    var button = document.createElement('button');
    var available = item.members ? item.members.some(isConnected) : isConnected(item.id);
    var on = item.members ? item.members.some(isOn) : isOn(item.id);
    button.className = 'device-tile' + (on ? ' on' : '') + (!available ? ' disconnected' : '');
    button.setAttribute('type', 'button');
    button.setAttribute('aria-label', item.title + ': ' + labelFor(item));
    button.innerHTML = '<span class="device-head"><span class="device-icon"></span><span class="device-title"></span></span><span class="device-state"></span>';
    button.querySelector('.device-icon').textContent = item.icon;
    button.querySelector('.device-title').textContent = item.title;
    button.querySelector('.device-state').textContent = labelFor(item);
    button.onclick = function () { toggleDevice(item); };
    return button;
  }
  function renderDeviceGrid(id) {
    var grid = byId(id);
    while (grid.firstChild) grid.removeChild(grid.firstChild);
    for (var i = 0; i < DEVICE_LIST.length; i += 1) grid.appendChild(makeDeviceTile(DEVICE_LIST[i]));
  }
  function valueOrDash(value, suffix) {
    if (value === null || value === undefined || value === '') return '-- ' + suffix;
    return value + ' ' + suffix;
  }
  function renderSensors() {
    var sensors = state.sensors || {};
    byId('overview-temperature').textContent = valueOrDash(sensors.temperatureC, '°C');
    byId('overview-humidity').textContent = valueOrDash(sensors.humidityPercent, '%');
    byId('overview-door').textContent = sensors.doorOpen === true ? 'ABIERTA' : sensors.doorOpen === false ? 'CERRADA' : 'SIN LECTURA';
    byId('overview-motion').textContent = 'Movimiento: ' + (sensors.motionDetected === true ? 'detectado' : sensors.motionDetected === false ? 'sin detectar' : 'sin lectura');
    byId('sensor-temperature').textContent = valueOrDash(sensors.temperatureC, '°C');
    byId('sensor-humidity').textContent = valueOrDash(sensors.humidityPercent, '%');
    byId('sensor-air').textContent = valueOrDash(sensors.airQualityPpm, 'ppm');
    byId('sensor-motion').textContent = sensors.motionDetected === true ? 'Detectado' : sensors.motionDetected === false ? 'Sin movimiento' : 'Sin lectura';
    byId('sensor-door').textContent = sensors.doorOpen === true ? 'Abierta' : sensors.doorOpen === false ? 'Cerrada' : 'Sin lectura';
    byId('sensor-window').textContent = sensors.windowOpen === true ? 'Abierta' : sensors.windowOpen === false ? 'Cerrada' : 'Sin lectura';
    byId('door-card').className = 'metric-card' + (sensors.doorOpen === true ? ' accent-teal alert-door' : '');
    var alerts = [];
    if (Number(sensors.temperatureC) >= 30) alerts.push(['Temperatura alta', 'Revisa el clima del aula.', true]);
    if (Number(sensors.humidityPercent) >= 70) alerts.push(['Humedad alta', 'Revisa ventilación y sensores.', false]);
    if (sensors.doorOpen === true) alerts.push(['Puerta abierta', 'La puerta del aula aparece abierta.', true]);
    if (sensors.motionDetected === true) alerts.push(['Movimiento', 'El sensor detecta movimiento.', false]);
    var alertList = byId('alert-list');
    while (alertList.firstChild) alertList.removeChild(alertList.firstChild);
    if (!alerts.length) { alertList.textContent = 'Sin alertas activas.'; return; }
    for (var i = 0; i < alerts.length; i += 1) {
      var card = document.createElement('div');
      card.className = 'alert-card' + (alerts[i][2] ? ' critical' : '');
      card.textContent = alerts[i][0] + ' · ' + alerts[i][1];
      alertList.appendChild(card);
    }
  }
  function render() {
    byId('classroom-label').textContent = 'Aula ' + config.classroom + ' · actualización automática';
    renderSensors();
    renderDeviceGrid('overview-devices');
    renderDeviceGrid('full-devices');
  }
  function sendDevice(id, enabled, done) {
    request('/api/v1/classrooms/' + encodeURIComponent(config.classroom) + '/devices/' + encodeURIComponent(id) + '/command', 'POST', { state: enabled ? 'on' : 'off' }, done);
  }
  function runCommands(ids, enabled, done) {
    var index = 0;
    function next(error) {
      if (error) { done(error); return; }
      if (index >= ids.length) { done(null); return; }
      var id = ids[index++];
      sendDevice(id, enabled, next);
    }
    next(null);
  }
  function toggleDevice(item) {
    if (busy) return;
    var ids = item.members || [item.id];
    var shouldTurnOn = !ids.every(isOn);
    busy = true;
    setStatus('Enviando comando a ' + item.title + '…');
    runCommands(ids, shouldTurnOn, function (error) {
      busy = false;
      if (error) { setError(error.message); setStatus('No se confirmó el cambio. Revisa el equipo.'); return; }
      setError('');
      setStatus(item.title + (shouldTurnOn ? ' encendido' : ' apagado') + ' · confirmando estado');
      refresh(true);
    });
  }
  function runScene(start) {
    if (busy) return;
    busy = true;
    setStatus(start ? 'Iniciando clase…' : 'Terminando clase…');
    runCommands(SCENE_ON, start, function (error) {
      busy = false;
      if (error) { setError('Escena incompleta: ' + error.message + ' Revisa los equipos.'); setStatus('No se confirmó toda la escena.'); return; }
      setError('');
      setStatus(start ? 'Escena Iniciar clase confirmada.' : 'Escena Terminar clase confirmada.');
      refresh(true);
    });
  }
  function selectPage(page) {
    activePage = page;
    var buttons = document.querySelectorAll('.nav-button[data-page]');
    for (var i = 0; i < buttons.length; i += 1) buttons[i].className = 'nav-button' + (buttons[i].getAttribute('data-page') === page ? ' selected' : '');
    var pages = document.querySelectorAll('.page');
    for (var j = 0; j < pages.length; j += 1) pages[j].className = 'page' + (pages[j].id === page + '-page' ? ' active' : '');
    byId('page-title').textContent = page === 'devices' ? 'Control de equipos' : page === 'sensors' ? 'Sensores y alertas' : 'Estado del aula';
  }
  function showSettings(show) {
    byId('settings-layer').className = 'settings-layer' + (show ? '' : ' hidden');
    byId('settings-error').className = 'settings-error hidden';
    if (show) {
      byId('gateway-url').value = config.url || '';
      byId('client-token').value = config.token || '';
      byId('classroom-id').value = config.classroom || 'aula-204';
      window.setTimeout(function () { byId('gateway-url').focus(); }, 50);
    }
  }
  function connect() {
    saveConfig();
    if (!config.url) { byId('settings-error').textContent = 'Escribe la URL del gateway.'; byId('settings-error').className = 'settings-error'; return; }
    byId('connect-button').textContent = 'Conectando…';
    request('/api/v1/config', 'GET', null, function (error, info) {
      byId('connect-button').textContent = 'Guardar y conectar';
      if (error) { byId('settings-error').textContent = error.message; byId('settings-error').className = 'settings-error'; return; }
      if (info.classroomId) config.classroom = info.classroomId;
      try { localStorage.setItem('smartclass.gateway', JSON.stringify(config)); } catch (ignore) {}
      showSettings(false);
      setError('');
      refresh(false);
      if (info.mode === 'home_assistant') setStatus('Gateway físico Home Assistant conectado.');
      else setStatus('Gateway de demostración conectado.');
    });
  }
  function visibleFocusable() {
    var all = document.querySelectorAll('button,input');
    var result = [];
    for (var i = 0; i < all.length; i += 1) {
      if (all[i].offsetWidth || all[i].offsetHeight || all[i] === document.activeElement) result.push(all[i]);
    }
    return result;
  }
  function moveFocus(direction) {
    var nodes = visibleFocusable();
    var current = document.activeElement;
    var rect = current && current.getBoundingClientRect ? current.getBoundingClientRect() : null;
    if (!rect) { if (nodes.length) nodes[0].focus(); return; }
    var cx = rect.left + rect.width / 2;
    var cy = rect.top + rect.height / 2;
    var best = null;
    var bestScore = Infinity;
    for (var i = 0; i < nodes.length; i += 1) {
      if (nodes[i] === current) continue;
      var r = nodes[i].getBoundingClientRect();
      var x = r.left + r.width / 2;
      var y = r.top + r.height / 2;
      var dx = x - cx;
      var dy = y - cy;
      var primary = direction === 'left' ? -dx : direction === 'right' ? dx : direction === 'up' ? -dy : dy;
      if (primary <= 2) continue;
      var secondary = direction === 'left' || direction === 'right' ? Math.abs(dy) : Math.abs(dx);
      var score = primary + secondary * 2.8;
      if (score < bestScore) { bestScore = score; best = nodes[i]; }
    }
    if (best) best.focus();
  }
  function goBack() {
    if (!byId('settings-layer').className.match(/hidden/)) { showSettings(false); return; }
    if (activePage !== 'overview') { selectPage('overview'); document.querySelector('[data-page="overview"]').focus(); return; }
    return false;
  }
  function onKeyDown(event) {
    var code = event.keyCode;
    var target = event.target || event.srcElement;
    var isInput = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA');
    if ((code === 37 || code === 38 || code === 39 || code === 40) && !isInput) {
      event.preventDefault();
      moveFocus(code === 37 ? 'left' : code === 38 ? 'up' : code === 39 ? 'right' : 'down');
    } else if ((code === 461 || code === 10009 || code === 27 || code === 8) && !isInput) {
      if (goBack() !== false) event.preventDefault();
    }
  }
  function startClock() {
    function tick() {
      var now = new Date();
      byId('clock').textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      byId('date-label').textContent = now.toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long' });
    }
    tick();
    window.setInterval(tick, 1000);
  }
  function init() {
    var nav = document.querySelectorAll('.nav-button[data-page]');
    for (var i = 0; i < nav.length; i += 1) nav[i].onclick = function () { selectPage(this.getAttribute('data-page')); };
    byId('settings-button').onclick = function () { showSettings(true); };
    byId('close-settings').onclick = function () { showSettings(false); };
    byId('cancel-settings').onclick = function () { showSettings(false); };
    byId('connect-button').onclick = connect;
    byId('refresh-button').onclick = function () { refresh(false); };
    var scenes = document.querySelectorAll('[data-scene]');
    for (var j = 0; j < scenes.length; j += 1) scenes[j].onclick = function () { runScene(this.getAttribute('data-scene') === 'start'); };
    document.addEventListener('keydown', onKeyDown, false);
    startClock();
    render();
    if (config.url) refresh(false); else showSettings(true);
    window.setInterval(function () { refresh(true); }, 5000);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, false); else init();
}());
