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
     * Notifica al paciente que su médico le agendó una cita de seguimiento
     * (CU-08, FA02).
     *
     * @param correoDestino correo electrónico del paciente
     * @param nombrePaciente nombre completo del paciente
     * @param nombreMedico nombre del médico de la cita
     * @param especialidad especialidad de la cita
     * @param sucursal sucursal donde se atenderá
     * @param fechaHora fecha y hora de la cita, ya formateada como texto
     */
    public void enviarNotificacionSeguimiento(String correoDestino, String nombrePaciente, String nombreMedico,
                                              String especialidad, String sucursal, String fechaHora) {
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(correoDestino);
            mensaje.setSubject("Cita de Seguimiento - Hospital Sistema Médico");
            mensaje.setText("Estimado(a) " + nombrePaciente + ",\n\n"
                    + "Su médico le agendó una cita de seguimiento:\n\n"
                    + "Médico: " + nombreMedico + "\n"
                    + "Especialidad: " + especialidad + "\n"
                    + "Sucursal: " + sucursal + "\n"
                    + "Fecha y hora: " + fechaHora + "\n\n"
                    + "Ingrese al portal para completar el pago y confirmar su cita.\n\n"
                    + "Este es un correo automático del Sistema Informático Hospitalario. No responda a este mensaje.");
            mailSender.send(mensaje);
        } catch (Exception e) {
            System.err.println("No se pudo enviar la notificación de seguimiento: " + e.getMessage());
        }
    }

    /**
     * Notifica al médico tratante que farmacia sustituyó un medicamento de su
     * receta (CU-11, FA02).
     *
     * @param correoDestino correo electrónico del médico
     * @param nombreMedico nombre completo del médico
     * @param paciente nombre del paciente
     * @param recetaId número de la receta
     * @param original medicamento recetado
     * @param alternativa medicamento entregado en su lugar
     * @param razon razón de la sustitución indicada por farmacia
     */
    public void enviarNotificacionSustitucion(String correoDestino, String nombreMedico, String paciente, Long recetaId,
                                              String original, String alternativa, String razon) {
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(correoDestino);
            mensaje.setSubject("Sustitución de medicamento - Receta #" + recetaId + " - Hospital Sistema Médico");
            mensaje.setText("Estimado(a) " + nombreMedico + ",\n\n"
                    + "Farmacia sustituyó un medicamento de la receta #" + recetaId + " del paciente " + paciente + ":\n\n"
                    + "Medicamento recetado: " + original + "\n"
                    + "Medicamento entregado: " + alternativa + "\n"
                    + "Razón: " + razon + "\n\n"
                    + "Este es un correo automático del Sistema Informático Hospitalario. No responda a este mensaje.");
            mailSender.send(mensaje);
        } catch (Exception e) {
            System.err.println("No se pudo enviar la notificación de sustitución: " + e.getMessage());
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