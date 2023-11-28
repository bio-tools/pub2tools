
var entities = {
	'&': '&amp;',
	'<': '&lt;',
	'>': '&gt;',
	'"': '&quot;',
	"'": '&#39;'
};
var escape = function(text) {
	return String(text).replace(/[&<>"']/g, function(s) { return entities[s] });
};

var get_params = function(selector) {
	var inputs = document.querySelectorAll(selector);
	var params = {};
	for (var i = 0; i < inputs.length; ++i) {
		var input = inputs[i];
		if (input.tagName == 'SELECT') {
			var options = input.getElementsByTagName('option');
			var differ = false;
			var selected = [];
			for (var j = 0; j < options.length; ++j) {
				var option = options[j];
				if (option.selected && option.dataset.default != 'selected' || !option.selected && option.dataset.default == 'selected') {
					if (input.multiple) {
						differ = true;
					} else {
						params[input.id] = input.value;
						break;
					}
				}
				if (input.multiple && option.selected) {
					selected.push(option.value);
				}
			}
			if (differ) {
				params[input.id] = selected;
			}
		} else if (input.type == 'checkbox') {
			if (input.checked && input.dataset.default != 'true' || !input.checked && input.dataset.default == 'true') {
				params[input.id] = input.checked;
			}
		} else if (input.value != input.dataset.default) {
			params[input.id] = input.value;
		}
	}
	return params;
}

var check = function(id, endpoint) {
	var input = document.getElementById(id);
	var output = document.getElementById(id + '-output');
	if (input.value === '') {
		input.classList.remove('input-good');
		input.classList.remove('input-bad');
		output.innerHTML = '';
		return;
	}
	input.readOnly = true;
	var request = new XMLHttpRequest();
	request.onreadystatechange = function() {
		if (request.readyState == 4 && request.status > 0) {
			input.classList.remove('input-medium');
			output.classList.remove('output-medium');
			var json = JSON.parse(request.responseText);
			var output_html = '';
			if (json.success) {
				input.classList.add('input-good');
				output.classList.add('output-good');
				var values = json[id];
				for (var key in values) {
					var value = values[key];
					if (id != 'annotations') {
						output_html += '<span';
						if (value.status != 'final' && value.status != 'totally final') {
							if (value.status != 'non-final') {
								output_html += ' class="output-bad"';
							} else {
								output_html += ' class="output-medium"';
							}
						}
						output_html += '>';
						if (value.id === Object(value.id)) {
							output_html += '[' + escape([value.id.pmid, value.id.pmcid, value.id.doi].filter(String).join(', ')) + ']';
						} else {
							output_html += escape(value.id);
						}
						output_html += ' : ' + escape(value.status) + '</span><br>';
					} else {
						output_html += '<span>' + escape(value.uri) + ' : ' + escape(value.label) + '</span><br>';
					}
				}
			} else {
				input.classList.add('input-bad');
				output.classList.add('output-bad');
				if (json.message) {
					output_html += '<span>' + escape(json.message) + '</span>';
				} else {
					output_html += '<span>Internal Server Error</span>';
				}
				if (json.time) {
					output_html += '<br><span>' + escape(json.time) + '</span>';
				}
			}
			output.innerHTML = output_html;
			input.readOnly = false;
		}
	}
	var params = {};
	if (id != 'annotations') {
		params = get_params('#tab-title-fetcherArgs ~ .tab-content > .param:not(.param-disabled) input:not([type=hidden]), #tab-title-fetcherArgs ~ .tab-content > .param select');
	}
	params[id] = input.value;
	request.open('POST', endpoint, true);
	request.setRequestHeader('Content-Type', 'application/json');
	request.send(JSON.stringify(params));
	input.classList.remove('input-good');
	output.classList.remove('output-good');
	input.classList.remove('input-bad');
	output.classList.remove('output-bad');
	input.classList.add('input-medium');
	output.classList.add('output-medium');
	output.innerHTML = '<span>Working...</span>';
};

var param = function() {
	var params = get_params('.param:not(.param-disabled) input:not([type=hidden]), .param select');
	var href = window.location.pathname;
	var i = 0;
	var i_max = 0;
	for (var key in params) {
		++i_max;
	}
	if (i_max > 0) {
		href += '?';
	}
	for (var key in params) {
		var value = params[key];
		if (Array.isArray(value) && value.length > 0) {
			for (var j = 0; j < value.length; ++j) {
				href += key + '=' + value[j];
				if (j < value.length - 1) {
					href += '&';
				}
			}
		} else {
			href += key + '=' + value;
		}
		if (i < i_max - 1) {
			href += '&';
		}
		++i;
	}
	href += window.location.hash;
	history.replaceState(null, '', href);
}

var biotoolsExisting = function(text, arr) {
	if (arr != null && arr.length > 0) {
		return '<span>' + text + ' ' + arr.map(e => '<a href="https://bio.tools/' + escape(e.split(' (')[0]) + '">' + escape(e.split(' (')[0]) + '</a>').join(', ') + '</span><br>';
	} else {
		return '';
	}
};

var run = function(id) {
	var buttonOutput = document.getElementById(id + '-output');
	var buttonOutputs = document.querySelectorAll('.button-output');
	var buttonInputs = document.querySelectorAll('.button input');
	buttonInputs.forEach(e => e.disabled = true);
	var request = new XMLHttpRequest();
	request.onreadystatechange = function() {
		if (request.readyState == 4 && request.status > 0) {
			buttonOutput.classList.remove('output-medium');
			var json = JSON.parse(request.responseText);
			if (json.success) {
				buttonOutput.classList.add('output-good');
				if (id == 'withoutmap' || id == 'all') {
					if (id == 'all') {
						var jsonWithoutMap = JSON.parse(JSON.stringify(json));
						delete jsonWithoutMap['tool']['function'];
						delete jsonWithoutMap['tool']['topic'];
						document.getElementById('pub2tools-results').value = JSON.stringify(jsonWithoutMap['tool'], null, 2);
					} else {
						document.getElementById('pub2tools-results').value = JSON.stringify(json['tool'], null, 2);
					}
					if (json['tool']['confidence_flag'] == 'high') {
						document.getElementById('pub2tools-results').classList.add('input-good');
					} else if (json['tool']['confidence_flag'] == 'medium') {
						document.getElementById('pub2tools-results').classList.add('input-medium');
					} else {
						document.getElementById('pub2tools-results').classList.add('input-bad');
					}
					var output_html = '';
					if (!json['status']['include']) {
						output_html += '<span>Not a tool!</span><br>';
					}
					if (json['status']['homepageBroken']) {
						output_html += '<span>Homepage broken!</span><br>';
					}
					if (json['status']['homepageMissing']) {
						output_html += '<span>Homepage missing!</span><br>';
					}
					output_html += biotoolsExisting('Existing in bio.tools as', json['status']['existing']);
					output_html += biotoolsExisting('Same publications and name as in', json['status']['publicationAndNameExisting']);
					output_html += biotoolsExisting('Same name and some publications in common with', json['status']['nameExistingSomePublicationDifferent']);
					output_html += biotoolsExisting('Some publications in common but name different from', json['status']['somePublicationExistingNameDifferent']);
					output_html += biotoolsExisting('Same name but publications different in', json['status']['nameExistingPublicationDifferent']);
					output_html += biotoolsExisting('Name similar to', json['status']['nameMatch']);
					if (json['status']['otherNames'] != null && json['status']['otherNames'].length > 0) {
						output_html += '<span>Correct name could also be ' + json['status']['otherNames'].map(e => '"' + escape(e) + '"').join(', ') + '</span><br>';
					}
					if (json['status']['toolsExtra'] != null && json['status']['toolsExtra'].length > 0) {
						output_html += '<span>Given publications could contain extra tools: ' + json['status']['toolsExtra'].map(e => '"' + escape(e) + '"').join(', ') + '</span><br>';
					}
					document.getElementById('pub2tools-results-output').innerHTML = output_html;
				}
				if (id == 'map' || id == 'all') {
					document.getElementById('to-biotools-output').innerHTML = escape(JSON.stringify(json['tool'], null, 2));
					document.getElementById('to-biotools-output').style.height = (document.getElementById('to-biotools-output').scrollHeight + 4) + 'px';
				}
				buttonOutput.innerHTML = '<span>Took ' + escape(json['time']['duration']) + ' seconds</span>';
			} else {
				buttonOutput.classList.add('output-bad');
				var output_html = '';
				if (json.message) {
					output_html += '<span>' + escape(json.message) + '</span>';
				} else {
					output_html += '<span>Internal Server Error</span>';
				}
				if (json.time) {
					output_html += '<br><span>' + escape(json.time) + '</span>';
				}
				buttonOutput.innerHTML = output_html;
			}
			buttonInputs.forEach(e => e.disabled = false);
		}
	}
	var params = get_params('.param:not(.param-disabled) input:not([type=hidden]), .param select');
	params['step'] = id;
	if (id == 'withoutmap' || id == 'all') {
		for (input of ['publicationIds', 'name', 'webpageUrls']) {
			if (document.getElementById(input).value != '') {
				params[input] = document.getElementById(input).value;
			}
		}
	} else {
		if (document.getElementById('pub2tools-results').value != '') {
			try {
				params['tool'] = JSON.parse(document.getElementById('pub2tools-results').value);
			} catch (error) {
				buttonOutput.innerHTML = '<span>' + escape(error) + '</span>';
				buttonOutput.classList.remove('output-good');
				buttonOutput.classList.add('output-bad');
				buttonInputs.forEach(e => e.disabled = false);
				return;
			}
		}
	}
	request.open('POST', '/pub2tools/api', true);
	request.setRequestHeader('Content-Type', 'application/json');
	request.send(JSON.stringify(params));
	buttonOutputs.forEach(e => e.innerHTML = '');
	buttonOutputs.forEach(e => e.classList.remove('output-good', 'output-bad'));
	if (id == 'withoutmap' || id == 'all') {
		document.getElementById('pub2tools-results').value = '';
		document.getElementById('pub2tools-results').classList.remove('input-good', 'input-medium', 'input-bad');
		document.getElementById('pub2tools-results-output').innerHTML = '';
	}
	document.getElementById('to-biotools-output').innerHTML = '';
	document.getElementById('to-biotools-output').style.height = '42px';
	buttonOutput.classList.add('output-medium');
	buttonOutput.innerHTML = '<span>Working...</span>';
};
