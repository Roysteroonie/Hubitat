/*
* Author: tguerena and surge919
*
* Device Handler
*
*
* Modified by Royski for Hubitat 14/09/2018
* Added states for on/off in the driver and can be used to execute webhooks 
* in External On URI and External Off URI Preferences
* 
*/


preferences {
	section("External Access"){
		input "external_on_uri", "text", title: "External On URI", required: false
		input "external_off_uri", "text", title: "External Off URI", required: false
	}
    
	section("Internal Access"){
		input "internal_ip", "text", title: "Internal IP", required: false
		input "internal_port", "text", title: "Internal Port (if not 80)", required: false
		input "internal_on_path", "text", title: "Internal On Path (/blah?q=this)", required: false
		input "internal_off_path", "text", title: "Internal Off Path (/blah?q=this)", required: false
	}
}




metadata {
	definition (name: "URI Switch", namespace: "tguerena", author: "Troy Guerena") {
		capability "Actuator"
			capability "Switch"
			capability "Sensor"
	}

}

def parse(String description) {
	log.debug(description)
}

def on() {
	if (external_on_uri){
		// sendEvent(name: "switch", value: "on")
		// log.debug "Executing ON"

		def cmd = "${settings.external_on_uri}";

		log.debug "Sending request cmd[${cmd}]"

			httpGet(cmd) {resp ->
				if (resp.data) {
					log.info "${resp.data}"
				} 
			}
			sendHubCommand(result)
			sendEvent(name: "switch", value: "on", isStateChange: true) 
			log.debug "Executing ON" 
			log.debug result
	}
	if (internal_on_path){
		def port
			if (internal_port){
				port = "${internal_port}"
			} else {
				port = 80
			}

		def result = new hubitat.device.HubAction(
				method: "GET",
				path: "${internal_on_path}",
				headers: [
				HOST: "${internal_ip}:${port}"
				]
				)
			sendHubCommand(result)
			sendEvent(name: "switch", value: "on", isStateChange: true) 
			log.debug "Executing ON" 
			log.debug result
	}
}

def off() {
	if (external_off_uri){
		def cmd = "${settings.external_off_uri}";
		log.debug "Sending request cmd[${cmd}]"
			httpGet(cmd) {resp ->
				if (resp.data) {
					log.info "${resp.data}"
				} 
			}
			sendHubCommand(result)
			sendEvent(name: "switch", value: "off", isStateChange: true)
			log.debug "Executing OFF" 
			log.debug result
	}
	if (internal_off_path){
		def port
			if (internal_port){
				port = "${internal_port}"
			} else {
				port = 80
			}

		def result = new hubitat.device.HubAction(
				method: "GET",
				path: "${internal_off_path}",
				headers: [
				HOST: "${internal_ip}:${port}"
				]
				)

			sendHubCommand(result)
			sendEvent(name: "switch", value: "off", isStateChange: true)
			log.debug "Executing OFF" 
			log.debug result
	}
}
