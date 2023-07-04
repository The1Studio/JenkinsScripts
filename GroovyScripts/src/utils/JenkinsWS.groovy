//file:noinspection GroovyAssignabilityCheck
package utils

import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl
import org.jenkinsci.plugins.workflow.cps.ParamsVariable
import org.jenkinsci.plugins.workflow.cps.RunWrapperBinder

import java.util.concurrent.TimeUnit

class JenkinsWS {
    EnvActionImpl.Binder env
    ParamsVariable params
    RunWrapperBinder currentBuild
    def ws

    JenkinsWS(def ws) {
        this.ws = ws
        this.params = ws.params
        this.env = ws.env
        this.currentBuild = ws.currentBuild
    }

    /**
     * Print message
     * @param message
     * @return
     */
    Object echo(String message) {
        return ws.echo(message)
    }

    /**
     * Error signal
     * @param message
     * @return
     */
    Object error(String message) {
        return ws.error(message)
    }

    /**
     * Wait for interactive input
     * @param message
     * @return
     */
    Object input(String message) {
        return ws.input(message)
    }

    /**
     * Wait for interactive input
     * @param message
     * @param id
     * @param ok
     * @param parameters
     * @param submitter
     * @param submitterParameter
     * @return
     */
    Object input(String message, String id, String ok, Map parameters, String submitter, String submitterParameter) {
        return ws.input(message, id, ok, parameters, submitter, submitterParameter)
    }

    /**
     * Checks if running on a Unix-like node
     * @return true if running on a Unix-like node
     */
    boolean isUnix() {
        return ws.isUnix()
    }

    /**
     * Set job properties
     * @param properties
     * @return
     */
    Object properties(Map properties) {
        return ws.properties(properties)
    }

    /**
     * Retry the body up to the given number of times
     * @param count
     * @param body
     * @return
     */
    Object retry(int count, Closure body) {
        return ws.retry(count, body)
    }

    /**
     * Sauce
     * @param credentialsId
     * @param body
     * @return
     */
    Object sauce(String credentialsId, Closure body) {
        return ws.sauce(credentialsId, body)
    }

    /**
     * Sleep
     * @param time
     * @return
     */
    Object sleep(int time) {
        return ws.sleep(time)
    }

    /**
     * Sleep
     * @param time
     * @param unit
     * @return
     */
    Object sleep(int time, TimeUnit unit) {
        return ws.sleep(time: time, unit: unit)
    }

    /**
     * Use a tool from a predefined Tool Installation
     * @param name
     * @return
     */
    Object tool(String name) {
        return ws.tool(name)
    }

    /**
     * Use a tool from a predefined Tool Installation
     * @param name
     * @param type
     * @return
     */
    Object tool(Map args) {
        return ws.tool(name: args.name, type: args.type)
    }

    /**
     * Wait for condition
     * @param body
     * @return
     */
    Object waitUntil(Closure body) {
        return ws.waitUntil(body)
    }

    /**
     * Set environment variables
     * @param overrides
     * @param body
     * @return
     */
    Object withEnv(Map overrides, Closure body) {
        return ws.withEnv(overrides, body)
    }

    /**
     * Write file to workspace
     * @param file
     * @param text
     * @param encoding
     * @return
     */
    Object writeFile(Map args) {
        return ws.writeFile(file: args.file, text: args.text, encoding: args.encoding)
    }

    /**
     * Run a shell script
     * @param script
     * @return
     */
    Object command(String runner, String script) {
        switch (runner) {
            case 'sh':
                return ws.sh(script)
            case 'bat':
                return ws.bat(script)
            case 'powershell':
                return ws.powershell(script)
            case 'pwsh':
                return ws.pwsh(script)
            case 'cmd':
                return ws.cmd(script)
            case 'bash':
                return ws.bash(script)
        }
        throw new Exception("Unknown runner: ${runner}")
    }

    /**
     * Run a shell script
     * @return
     */
    Object command(Map args) {

    }
}
