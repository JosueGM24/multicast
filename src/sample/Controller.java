package sample;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.net.*;
import java.io.*;

public class Controller {

    @FXML
    private TextArea console;

    @FXML
    private TextField multicast;

    @FXML
    private TextField port;

    @FXML
    private TextField urlApi;

    private MulticastSocket currentSocket;
    private Task<Void> currentTask;

    @FXML
    void listen(MouseEvent event) {
        if (currentTask != null && currentTask.isRunning()) {
            updateConsole("Cerrando conexión existente...");
            currentTask.cancel();
            console.clear();
            if (currentSocket != null && !currentSocket.isClosed()) {
                try {
                    currentSocket.leaveGroup(InetAddress.getByName(multicast.getText()));
                    currentSocket.close();
                } catch (IOException e) {
                    updateConsole("Error al cerrar el socket multicast: " + e.getMessage());
                }
            }
        }

        if (!multicast.getText().isEmpty() && !port.getText().isEmpty()) {
            String MCAST_GRP = multicast.getText();
            int MCAST_PORT = Integer.parseInt(port.getText());
            byte[] buffer = new byte[10240];
            currentTask = new Task<Void>() {
                @Override
                protected Void call() {
                    try {
                        currentSocket = new MulticastSocket(MCAST_PORT);
                        currentSocket.setReuseAddress(true);

                        InetAddress mcastGroup = InetAddress.getByName(MCAST_GRP);
                        currentSocket.joinGroup(mcastGroup);
                        String union = "Unido al grupo multicast " + MCAST_GRP + " en el puerto " + MCAST_PORT;
                        updateConsole(union);

                        while (!isCancelled()) {
                            updateConsole("Esperando datos...");
                            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                            currentSocket.receive(packet);

                            String operacion = new String(packet.getData(), 0, packet.getLength()).trim();  // Decodificar la operación recibida

                            if (urlApi.getText().isEmpty()) {
                                updateConsole("Datos recibidos");
                                updateConsole("Operación recibida: " + operacion);
                            } else {
                                updateConsole("Datos recibidos");
                                updateConsole("Operación recibida: " + operacion);

                                try {
                                    operacion = operacion.replaceAll("\\+", "%2B");
                                    System.out.println(operacion);
                                    URL url = new URL(urlApi.getText() + operacion);
                                    updateConsole("Procesando en " + url);

                                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                                    connection.setRequestMethod("GET");

                                    if (connection.getResponseCode() == 200) {
                                        try (InputStream inputStream = connection.getInputStream();
                                             JsonReader jsonReader = Json.createReader(inputStream)) {
                                            JsonObject location = jsonReader.readObject();
                                            String result = location.getString("result", "No result found");
                                            updateConsole("Result: " + result);
                                        } catch (Exception e) {
                                            updateConsole("Error leyendo la respuesta JSON: " + e.getMessage());
                                        }

                                    } else {
                                        updateConsole("Error en la respuesta del servidor: " + connection.getResponseMessage());
                                    }
                                } catch (IOException e) {
                                    updateConsole("Error en la conexión HTTP: " + e.getMessage());
                                }
                            }
                        }
                    } catch (IOException e) {
                        updateConsole("Error en el socket multicast: " + e.getMessage());
                    } finally {
                        if (currentSocket != null && !currentSocket.isClosed()) {
                            try {
                                currentSocket.leaveGroup(InetAddress.getByName(MCAST_GRP));
                                currentSocket.close();
                            } catch (IOException e) {
                                updateConsole("Error al cerrar el socket multicast: " + e.getMessage());
                            }
                        }
                    }
                    return null;
                }
            };
            Thread thread = new Thread(currentTask);
            thread.setDaemon(true);
            thread.start();
        } else {
            updateConsole("Escriba un grupo y puerto correctos");
        }
    }

    private void updateConsole(String message) {
        Platform.runLater(() -> console.appendText(message + "\n"));
    }

    public void clear(MouseEvent mouseEvent) {
        console.clear();
    }
}
