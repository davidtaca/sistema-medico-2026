package com.hospital.sistemamedico.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void enviarCorreoBienvenida(String correoDestino, String nombrePaciente) {
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(correoDestino);
            mensaje.setSubject("Bienvenido al Sistema de Citas - Hospital Sistema Médico");
            mensaje.setText("Estimado(a) " + nombrePaciente + ", su registro ha sido completado exitosamente. "
                    + "Ya puede agendar sus citas médicas a través de nuestro portal.");
            mailSender.send(mensaje);
        } catch (Exception e) {
            // No queremos que un fallo de correo bloquee el registro del usuario
            System.err.println("No se pudo enviar el correo de bienvenida: " + e.getMessage());
        }
    }
    public void enviarComprobantePago(String correoDestino, String nombrePaciente, String numeroTransaccion,
                                      String monto, String nombreMedico, String especialidad, String sucursal, String fechaHora) {
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(correoDestino);
            mensaje.setSubject("Comprobante de Pago - Cita Médica - Hospital Sistema Médico");
            mensaje.setText("Estimado(a) " + nombrePaciente + ",\n\n"
                    + "Su pago ha sido procesado exitosamente. Detalle del comprobante:\n\n"
                    + "Número de transacción: " + numeroTransaccion + "\n"
                    + "Monto pagado: Q" + monto + "\n"
                    + "Médico: " + nombreMedico + "\n"
                    + "Especialidad: " + especialidad + "\n"
                    + "Sucursal: " + sucursal + "\n"
                    + "Fecha y hora de la cita: " + fechaHora + "\n\n"
                    + "Gracias por confiar en nuestro hospital.");
            mailSender.send(mensaje);
        } catch (Exception e) {
            System.err.println("No se pudo enviar el comprobante de pago: " + e.getMessage());
        }
    }
}