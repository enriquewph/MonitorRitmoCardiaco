/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ar.cba.utn.oximetro;

import com.fazecast.jSerialComm.SerialPort;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import org.jfree.chart.plot.XYPlot;

public class DataPlotter {

    public static void main(String[] args) throws InterruptedException {
        //Variables 
        double valueMax = 5000;
        double picoValueMax = 2500;
        double cuentas = 0;
        boolean picoFlag = false;
        int picoCount = 0;
        int maxPoints = 10000;
        int pulsos = 0;
        double picoTimeMax = 0.5;
        long firstPicoTime = 0;
        long picoTimeSize = 0;
        long startTime = System.currentTimeMillis();
        long timeWindow = 10000; // 10 segundos

        //Configuracion del puerto serial
        SerialPort serialPort = SerialPort.getCommPort("COM8");
        serialPort.setComPortParameters(115200, 8, 1, 0);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);

        // Configura la interfaz gráfica
        XYSeries series = new XYSeries("Curva oximetria");
        XYSeriesCollection dataset = new XYSeriesCollection(series);
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Oximetria Data Plot",
                "Tiempo (S)",
                "",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );
        XYPlot plot = chart.getXYPlot();

        plot.setBackgroundPaint(Color.BLACK);
        plot.getRangeAxis().setRange(-5000, 15000);
        
        // Crea la ventana gráfica
        JFrame frame = new JFrame("Arduino Real-Time Plotter");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Create a label to display the pulse count
        JLabel pulsosLabel = new JLabel("Pulsos: ");
        pulsosLabel.setFont(new Font("Arial", Font.BOLD, 20));
        frame.getContentPane().add(pulsosLabel, BorderLayout.PAGE_END);

        // Configura el panel del gráfico
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setBackground(Color.RED);

        // Añade el panel del gráfico al centro y el JLabel abajo al medio
        frame.getContentPane().add(chartPanel, BorderLayout.CENTER);

        // Establece el color de fondo del frame
        frame.getContentPane().setBackground(Color.WHITE);

        // Hace que el frame tenga un tamaño adecuado y lo hace visible
        frame.pack();
        frame.setVisible(true);

        if (serialPort.openPort()) {
            try {

                Thread.sleep(3000);
                SerialWrite("A1", serialPort);
                //BufferedReader br = new BufferedReader(new InputStreamReader(serialPort.getInputStream()));
                long count = System.currentTimeMillis();
                BufferedReader br = new BufferedReader(new FileReader("D:\\Informacion\\Datos\\test.csv"));

                while (true) {
                    String line = br.readLine();

                    if (line != null) {
                        //Convertir datos 
                        try {
                            cuentas = Double.parseDouble(line);
                        } catch (NumberFormatException e) {
                            cuentas = 0;
                        }
                        //Detector de picos
                        if (cuentas > picoValueMax) {
                            picoFlag = true;
                        }

                        if (picoFlag && cuentas < picoValueMax) {
                            picoFlag = false;
                            picoCount++;
                            if (picoCount == 1) {
                                firstPicoTime = System.currentTimeMillis() - count;
                            }
                            SerialWrite("A", serialPort);
                            if (picoCount == 2) {
                                picoTimeSize = (System.currentTimeMillis() - count) - firstPicoTime;
                                pulsos = (int) ((60 * 2) / (picoTimeSize / 1000.0));
                                pulsosLabel.setText("Pulsos: " + pulsos);
                                picoCount = 0;
                                firstPicoTime = System.currentTimeMillis();
                            }
                        }
                        //Actualizacion de grafico 
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        series.add(elapsedTime, cuentas);
                        if (series.getItemCount() > maxPoints) {
                            series.remove(0);
                        }
                        chart.getXYPlot().getDomainAxis().setRange(elapsedTime - timeWindow, elapsedTime);
                        chart.fireChartChanged();
                        chartPanel.repaint();
                    } else {
                        break;
                    }

                    Thread.sleep(2);
                }
            } catch (IOException | InterruptedException e) {
            } finally {
                SerialWrite("A0", serialPort);
                Thread.sleep(10);
                serialPort.closePort();
            }
        } else {
            System.err.println("Error: No se pudo abrir el puerto serial.");
        }
    }

    public static void SerialWrite(String mensaje, SerialPort serialPort) {
        byte[] messageBytesStop = mensaje.getBytes();
        serialPort.writeBytes(messageBytesStop, messageBytesStop.length);
    }
}
