/**
 *
 *	Logging kindly provided by Eric Vitale
 *  Copyright 2016 ericvitale@gmail.com
 *  You can find my other device handlers & SmartApps @ https://github.com/ericvitale
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 **/


    preferences {
        input "useActLog", "bool", title: "On/Off/Level Act. Feed", required: true, defaultValue: true
        input "useActLogDebug", "bool", title: "Debug Act. Feed", required: true, defaultValue: false
        input "logging", "enum", title: "Log Level", required: false, defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
    }
}


def initialize() {
	log("Initializing...", "DEBUG")
    setUseActivityLog(useActLog)
    setUseActivityLogDebug(useActLogDebug)
}

private determineLogLevel(data) {
    switch (data?.toUpperCase()) {
        case "TRACE":
            return 0
            break
        case "DEBUG":
            return 1
            break
        case "INFO":
            return 2
            break
        case "WARN":
            return 3
            break
        case "ERROR":
        	return 4
            break
        default:
            return 1
    }
}

def log(data, type) {
    data = "LIFX-GoG -- ${device.label} -- ${data ?: ''}"
        
    if (determineLogLevel(type) >= determineLogLevel(settings?.logging ?: "INFO")) {
        switch (type?.toUpperCase()) {
            case "TRACE":
                log.trace "${data}"
                break
            case "DEBUG":
                log.debug "${data}"
                break
            case "INFO":
                log.info "${data}"
                break
            case "WARN":
                log.warn "${data}"
                break
            case "ERROR":
                log.error "${data}"
                break
            default:
                log.error "LIFX-GoG -- ${device.label} -- Invalid Log Setting"
        }
    }
}

// Log examples

// Info
def on(duration=getDefaultStateTransitionDuration()) {
	log("Turning on...", "INFO")
    sendLIFXCommand(["power" : "on", "duration" : duration])
    sendEvent(name: "switch", value: "on", displayed: getUseActivityLog(), data: [syncing: "false"])
    sendEvent(name: "level", value: "${state.level}", displayed: getUseActivityLog())
}

// Debug
def setLevel(level, duration=getDefaultTransitionDuration()) {
	log("Begin setting groups level to ${value} over ${duration} seconds.", "DEBUG")
    if (level > 100) {
		level = 100
	} else if (level <= 0 || level == null) {
		sendEvent(name: "level", value: 0)
		return off()
	}
    state.level = level
	sendEvent(name: "level", value: level, displayed: getUseActivityLog())
    sendEvent(name: "switch", value: "on", displayed: false)
    def brightness = level / 100
    sendLIFXCommand(["brightness": brightness, "power": "on", "duration" : duration])
}

// Info
def setState(value) {
    if(color == "random") {
        def clSize = colorList().size()
    	def myColor = getRandom(clSize)
        log.info "Setting random color: ${myColor.name}"
        color = ["hue":myColor.hue, "saturation":myColor.sat/100, "brightness":myColor.lvl/100]
        brightness = null
        log.info color

}

// Error
def runEffect(effect="pulse", color="", from_color="", cycles=5, period=0.5, brightness=0.5) {
	log("runEffect(effect=${effect}, color=${color}: 1.0, from_color=${from_color}, cycles=${cycles}, period=${period}, brightness=${brightness}.", "DEBUG")
	if(effect != "pulse" && effect != "breathe") {
    	log("${effect} is not a value effect, defaulting to pulse.", "ERROR")
        effect = "pulse"
    }
    runLIFXEffect(["color" : "${color.toLowerCase()} brightness:${brightness}".trim(), "from_color" : "${from_color.toLowerCase()} brightness:${brightness}".trim(), "cycles" : "${cycles}" ,"period" : "${period}"], effect)
}

// Warn

def retry() {
	if(getRetryCount() < getMaxRetry()) {
    	log("Retrying command...", "INFO")
        incRetryCount()
		runIn(getRetryWait(5, getRetryCount()), sendLastCommand )
    } else {
    	log("Too many retries...", "WARN")
        resetRetryCount()
    }
}

// Trace

def putResponseHandler(response, data) {
    if(response.getStatus() == 200 || response.getStatus() == 207) {
		log("Response received from LFIX in the putReponseHandler.", "DEBUG")
        log("Response = ${response.getJson()}", "DEBUG")
        def totalBulbs = response.getJson().results.size()
        def results = response.getJson().results
        def bulbsOk = 0
        
        for(int i=0;i<totalBulbs;i++) {
        	if(results[i].status != "ok") {
        		log("${results[i].label} is ${results[i].status}.", "WARN")
            } else {
            	bulbsOk++
            	log("${results[i].label} is ${results[i].status}.", "TRACE")
            }
        }
        if(bulbsOk == totalBulbs) { 
            log("${bulbsOk} of ${totalBulbs} bulbs returned ok.", "INFO")
            resetRetryCount()
        } else {
        	log("${bulbsOk} of ${totalBulbs} bulbs returned ok.", "WARN")
            log("Retry Count = ${getRetryCount()}.", "INFO")
            retry()
        }
        updateLightStatus("${bulbsOk} of ${totalBulbs}")
    } else {
    	log("LIFX failed to adjust group. LIFX returned ${response.getStatus()}.", "ERROR")
        log("Error = ${response.getErrorData()}", "ERROR")
    }
}

// Think these are used for logging activity

def getUseActivityLog() {
	if(state.useActivityLog == null) {
    	state.useActivityLog = true
    }
	return state.useActivityLog
}

def setUseActivityLog(value) {
	state.useActivityLog = value
}

def getUseActivityLogDebug() {
	if(state.useActivityLogDebug == null) {
    	state.useActivityLogDebug = false
    }
    return state.useActivityLogDebug
}

def setUseActivityLogDebug(value) {
	state.useActivityLogDebug = value
}
