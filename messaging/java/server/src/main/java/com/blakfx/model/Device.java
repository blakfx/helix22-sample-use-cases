package com.blakfx.model;

/**
 * Represents any device, and allows for add/remove/toggle operations.
 * Additionally, chat is implemented alongside it. That allows for the eventual
 * integration of device actions into the chat client, as user commands.
 * Based from: https://www.oracle.com/webfolder/technetwork/tutorials/obe/java/HomeWebsocket/WebsocketHome.html
 */
public class Device {

    /**
     * The ID of the device.
     */
    private int id;
    /**
     * The name of the device.
     */
    private String name;
    /**
     * The status of the device.
     */
    private String status;
    /**
     * The type of the device.
     */
    private String type;
    /**
     * The description of the device.
     */
    private String description;
    /**
     * The owner of this device.
     */
    private String owner;

    /**
     * Constructs an "empty" device.
     */
    public Device() {}
    
    /**
     * Get this device's ID.
     * @return the device ID
     */
    public int getId() {
        return id;
    }
    
    /**
     * Get this device's name.
     * @return the device name
     */
    public String getName() {
        return name;
    }

    /**
     * Get this device's status.
     * @return the device status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Get this device's type.
     * @return the device type
     */
    public String getType() {
        return type;
    }
    
    /**
     * Get this device's description.
     * @return the device description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get this device's owner.
     * @return the device owner
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Set this device's id to a given value.
     * @param id the value to set the id to
     */
    public void setId(int id) {
        this.id = id;
    }
    
    /**
     * Set this device's name to a given value.
     * @param name the value to set the name to
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Set this device's status to a given value.
     * @param status the value to set the status to
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Set this device's type to a given value.
     * @param type the value to set the type to
     */
    public void setType(String type) {
        this.type = type;
    }
    
    /**
     * Set this device's description to a given value.
     * @param description the value to set the description to
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Set this device's owner to a given value.
     * @param owner the value to set the owner to
     */
    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * Define how to print a device as a neatly formatted string.
     */
    @Override
    public String toString() {
        return super.toString() + " {" + name + ", " + status + ", " + type + ", " + description + "} ";
    }
}