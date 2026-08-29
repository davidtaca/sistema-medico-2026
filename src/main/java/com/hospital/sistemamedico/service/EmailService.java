package com.hospital.sistemamedico.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Servicio encargado del envío de correos electrónicos reales del sistema,
 * usando Gmail SMTP configurado en application.properties. Los errores de
 * envío se capturan y solo se registran en consola: un fallo al enviar un
 * correo nunca debe bloquear el registro del usuario ni el pago, ya que son
 * procesos secundarios (notificación), no la operación principal.
 */
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Envía el correo de bienvenida a un paciente recién registrado (CU-02, paso final).
     *
     * @param correoDestino correo electrónico del paciente
     * @param nombrePaciente nombre completo del paciente, usado en el saludo
     */
    public void enviarCorreoBienvenida(String correoDestino, String nombrePaciente) {
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(correoDestino);
            mensaje.setSubject("Bienvenido al Sistema de Citas - Hospital Sistema Médico");
            mensaje.setText("Estimado(a) " + nombrePaciente + ", su registro ha sido completado exitosamente. "
                    + "Ya puede agendar sus citas médicas a través de nuestro portal.");
            mailSender.send(mensaje);
        } catch (Exception e) {
            // Un fallo de envío de correo no debe impedir que el registro del usuario se complete
            System.err.println("No se pudo enviar el correo de bienvenida: " + e.getMessage());
        }
    }

    /**
     * Envía el comprobante de pago por correo al paciente tras un pago exitoso
     * (CU-04, RN-CU04-05), con el detalle completo de la transacción y de la cita pagada.
     *
     * @param correoDestino correo electrónico del paciente
     * @param nombrePaciente nombre completo del paciente
     * @param numeroTransaccion número único de la transacción de pago
     * @param monto monto pagado, ya formateado como texto
     * @param nombreMedico nombre del médico asignado a la cita
     * @param especialidad nombre de la especialidad de la cita
     * @param sucursal nombre de la sucursal donde se atenderá
     * @param fechaHora fecha y hora de la cita, ya formateada como texto
     */
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