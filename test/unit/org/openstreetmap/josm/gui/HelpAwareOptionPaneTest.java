package org.openstreetmap.josm.gui;

import java.util.function.Function;

public class HelpAwareOptionPaneTest {
    /**
     * Sets the robot.
     * <p>
     * This function sets the {@link HelpAwareOptionPane#robot robot}, that should only
     * be used for testing. That's why the function is here.
     * <p>
     * Usage example:
     * <pre>{@code
     * List<Object> messages = new ArrayList<>();
     * HelpAwareOptionPaneTest.setRobot(o -> { messages.add(o); return 0; });
     * functionThatCallsTheHelpAwareOptionPaneWith("Message 1");
     * functionThatCallsTheHelpAwareOptionPaneWith("Message 2");
     * assertEquals(List.of(
     *     "&lt;html>Message 1&lt;/html>",
     *     "&lt;html>Message 2&lt;/html>"
     * ), messages);
     * }</pre>
     *
     * @param robot the robot
     */
    public static void setRobot(Function<Object, Integer> robot) {
        HelpAwareOptionPane.robot = robot;
    }
}
