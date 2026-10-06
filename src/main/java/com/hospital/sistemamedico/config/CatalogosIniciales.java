package com.hospital.sistemamedico.config;

import com.hospital.sistemamedico.model.Cie10;
import com.hospital.sistemamedico.model.Examen;
import com.hospital.sistemamedico.model.InventarioMedicamento;
import com.hospital.sistemamedico.model.Medicamento;
import com.hospital.sistemamedico.model.Sucursal;
import com.hospital.sistemamedico.repository.Cie10Repository;
import com.hospital.sistemamedico.repository.ExamenRepository;
import com.hospital.sistemamedico.repository.InventarioMedicamentoRepository;
import com.hospital.sistemamedico.repository.MedicamentoRepository;
import com.hospital.sistemamedico.repository.SucursalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

/**
 * Carga, al arrancar la aplicación, los catálogos que necesita la consulta
 * médica (CU-08) si todavía están vacíos: diagnósticos CIE-10 de uso común,
 * exámenes de laboratorio y medicamentos. Es un juego inicial; el CU-14
 * (mantenimiento de catálogos) permitirá administrarlos después.
 */
@Component
public class CatalogosIniciales implements CommandLineRunner {

    @Autowired
    private Cie10Repository cie10Repository;

    @Autowired
    private ExamenRepository examenRepository;

    @Autowired
    private MedicamentoRepository medicamentoRepository;

    @Autowired
    private InventarioMedicamentoRepository inventarioRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    /** Examen del catálogo inicial: monto en quetzales, unidad y rango de referencia de ejemplo. */
    private record DatosExamen(String nombre, BigDecimal precio, String unidad, String rango) {
        Examen aExamen() { return new Examen(nombre, precio, unidad, rango); }
    }

    private static DatosExamen ex(String nombre, String precio, String unidad, String rango) {
        return new DatosExamen(nombre, new BigDecimal(precio), unidad, rango);
    }

    private static final List<DatosExamen> EXAMENES = List.of(
            ex("Hemograma completo", "75", "g/dL", "Hemoglobina 12 - 16"),
            ex("Glucosa en ayunas", "35", "mg/dL", "70 - 100"),
            ex("Hemoglobina glicosilada (HbA1c)", "120", "%", "4.0 - 5.6"),
            ex("Perfil lipídico", "150", "mg/dL", "Colesterol total < 200"),
            ex("Colesterol total", "40", "mg/dL", "< 200"),
            ex("Triglicéridos", "40", "mg/dL", "< 150"),
            ex("Creatinina sérica", "45", "mg/dL", "0.6 - 1.3"),
            ex("Nitrógeno ureico (BUN)", "40", "mg/dL", "7 - 20"),
            ex("Ácido úrico", "40", "mg/dL", "3.5 - 7.2"),
            ex("Transaminasas (TGO/TGP)", "90", "U/L", "TGO 10 - 40 / TGP 7 - 56"),
            ex("Examen general de orina", "30", "N/A", null),
            ex("Examen general de heces", "30", "N/A", null),
            ex("Prueba de embarazo", "50", "N/A", "Negativo"),
            ex("Hormona estimulante de tiroides (TSH)", "110", "mIU/L", "0.4 - 4.0"),
            ex("Proteína C reactiva", "70", "mg/L", "< 5"),
            ex("Prueba rápida de dengue", "120", "N/A", "Negativo"),
            ex("Urocultivo", "100", "UFC/mL", "< 10,000"),
            ex("Tiempo de protrombina (TP/TPT)", "85", "seg", "11 - 13.5")
    );

    /** Medicamento del catálogo inicial: precio en quetzales, stock mínimo y stock inicial de ejemplo (null = sin inventario). */
    private record DatosMedicamento(String nombre, BigDecimal precio, Integer minimo, Integer stock) {
        Medicamento aMedicamento() { return new Medicamento(nombre, precio, minimo); }
    }

    private static DatosMedicamento med(String nombre, String precio, int minimo, Integer stock) {
        return new DatosMedicamento(nombre, new BigDecimal(precio), minimo, stock);
    }

    private static final List<DatosMedicamento> MEDICAMENTOS = List.of(
            med("Acetaminofén 500 mg", "8", 20, 200),
            med("Ibuprofeno 400 mg", "10", 20, 150),
            med("Diclofenaco 50 mg", "12", 15, 100),
            med("Amoxicilina 500 mg", "25", 20, 80),
            med("Azitromicina 500 mg", "45", 10, 40),
            med("Ciprofloxacino 500 mg", "30", 10, 60),
            med("Metronidazol 500 mg", "18", 10, 70),
            med("Omeprazol 20 mg", "15", 15, 90),
            med("Loratadina 10 mg", "12", 10, 80),
            med("Salbutamol inhalador", "65", 5, 25),
            med("Losartán 50 mg", "20", 15, 100),
            med("Enalapril 10 mg", "14", 15, 100),
            med("Metformina 850 mg", "16", 15, 120),
            med("Glibenclamida 5 mg", "12", 10, 60),
            med("Atorvastatina 20 mg", "35", 10, 70),
            med("Levotiroxina 50 mcg", "40", 10, 50),
            med("Sales de rehidratación oral", "6", 20, 100),
            med("Albendazol 400 mg", "22", 10, 12),
            med("Ranitidina 150 mg", "14", 10, null),
            med("Dexametasona 4 mg", "18", 10, null)
    );

    @Override
    public void run(String... args) {
        if (cie10Repository.count() == 0) {
            cie10Repository.saveAll(List.of(
                    new Cie10("A09", "Diarrea y gastroenteritis de presunto origen infeccioso"),
                    new Cie10("A90", "Dengue clásico"),
                    new Cie10("B34.9", "Infección viral, no especificada"),
                    new Cie10("B82.9", "Parasitosis intestinal, sin otra especificación"),
                    new Cie10("D64.9", "Anemia, no especificada"),
                    new Cie10("E03.9", "Hipotiroidismo, no especificado"),
                    new Cie10("E10.9", "Diabetes mellitus tipo 1 sin complicaciones"),
                    new Cie10("E11.9", "Diabetes mellitus tipo 2 sin complicaciones"),
                    new Cie10("E66.9", "Obesidad, no especificada"),
                    new Cie10("E78.5", "Hiperlipidemia, no especificada"),
                    new Cie10("F32.9", "Episodio depresivo, no especificado"),
                    new Cie10("F41.9", "Trastorno de ansiedad, no especificado"),
                    new Cie10("G43.9", "Migraña, no especificada"),
                    new Cie10("H10.9", "Conjuntivitis, no especificada"),
                    new Cie10("H66.9", "Otitis media, no especificada"),
                    new Cie10("I10", "Hipertensión esencial (primaria)"),
                    new Cie10("J00", "Rinofaringitis aguda (resfriado común)"),
                    new Cie10("J02.9", "Faringitis aguda, no especificada"),
                    new Cie10("J03.9", "Amigdalitis aguda, no especificada"),
                    new Cie10("J06.9", "Infección aguda de las vías respiratorias superiores, no especificada"),
                    new Cie10("J18.9", "Neumonía, no especificada"),
                    new Cie10("J20.9", "Bronquitis aguda, no especificada"),
                    new Cie10("J30.4", "Rinitis alérgica, no especificada"),
                    new Cie10("J45.9", "Asma, no especificada"),
                    new Cie10("K21.9", "Enfermedad del reflujo gastroesofágico sin esofagitis"),
                    new Cie10("K29.7", "Gastritis, no especificada"),
                    new Cie10("K52.9", "Gastroenteritis y colitis no infecciosas, no especificadas"),
                    new Cie10("K59.0", "Estreñimiento"),
                    new Cie10("L20.9", "Dermatitis atópica, no especificada"),
                    new Cie10("L30.9", "Dermatitis, no especificada"),
                    new Cie10("M25.5", "Dolor en articulación"),
                    new Cie10("M54.5", "Lumbago no especificado"),
                    new Cie10("N39.0", "Infección de vías urinarias, sitio no especificado"),
                    new Cie10("N76.0", "Vaginitis aguda"),
                    new Cie10("R05", "Tos"),
                    new Cie10("R07.4", "Dolor torácico, no especificado"),
                    new Cie10("R10.4", "Otros dolores abdominales y los no especificados"),
                    new Cie10("R11", "Náusea y vómito"),
                    new Cie10("R42", "Mareo y desvanecimiento"),
                    new Cie10("R50.9", "Fiebre, no especificada"),
                    new Cie10("R51", "Cefalea"),
                    new Cie10("S93.4", "Esguince y torcedura del tobillo"),
                    new Cie10("T14.9", "Traumatismo, no especificado"),
                    new Cie10("Z00.0", "Examen médico general"),
                    new Cie10("Z23", "Necesidad de inmunización contra enfermedad bacteriana única"),
                    new Cie10("Z34.9", "Supervisión de embarazo normal, no especificada")
            ));
        }

        if (examenRepository.count() == 0) {
            examenRepository.saveAll(EXAMENES.stream().map(d -> d.aExamen()).toList());
        } else {
            // Catálogo creado antes de que los exámenes tuvieran monto (CU-09): se completa sin tocar lo ya definido
            for (Examen e : examenRepository.findAll()) {
                if (e.getPrecio() == null) {
                    DatosExamen d = EXAMENES.stream().filter(x -> x.nombre.equals(e.getNombre())).findFirst().orElse(null);
                    e.setPrecio(d != null ? d.precio : new BigDecimal("50"));
                    if (d != null) {
                        e.setUnidad(d.unidad);
                        e.setRangoReferencia(d.rango);
                    }
                    examenRepository.save(e);
                }
            }
        }

        if (medicamentoRepository.count() == 0) {
            medicamentoRepository.saveAll(MEDICAMENTOS.stream().map(d -> d.aMedicamento()).toList());
        } else {
            // Catálogo creado antes de que los medicamentos tuvieran precio y stock mínimo (CU-11)
            for (Medicamento m : medicamentoRepository.findAll()) {
                if (m.getPrecio() == null) {
                    DatosMedicamento d = MEDICAMENTOS.stream().filter(x -> x.nombre.equals(m.getNombre())).findFirst().orElse(null);
                    m.setPrecio(d != null ? d.precio : new BigDecimal("25"));
                    m.setStockMinimo(d != null ? d.minimo : Integer.valueOf(10));
                    medicamentoRepository.save(m);
                }
            }
        }

        // Inventario inicial de ejemplo en cada sucursal, solo si todavía no existe ninguno (CU-11).
        // Ranitidina y Dexametasona quedan sin inventario a propósito para poder ver el caso "Sin inventario registrado".
        if (inventarioRepository.count() == 0) {
            for (Sucursal sucursal : sucursalRepository.findAll()) {
                for (DatosMedicamento d : MEDICAMENTOS) {
                    if (d.stock != null) {
                        medicamentoRepository.findAll().stream().filter(m -> m.getNombre().equals(d.nombre)).findFirst()
                                .ifPresent(m -> inventarioRepository.save(new InventarioMedicamento(m, sucursal, d.stock)));
                    }
                }
            }
        }
    }
}
