package com.hospital.sistemamedico.config;

import com.hospital.sistemamedico.model.Cie10;
import com.hospital.sistemamedico.model.Examen;
import com.hospital.sistemamedico.model.Medicamento;
import com.hospital.sistemamedico.repository.Cie10Repository;
import com.hospital.sistemamedico.repository.ExamenRepository;
import com.hospital.sistemamedico.repository.MedicamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

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
            examenRepository.saveAll(Stream.of(
                    "Hemograma completo", "Glucosa en ayunas", "Hemoglobina glicosilada (HbA1c)",
                    "Perfil lipídico", "Colesterol total", "Triglicéridos", "Creatinina sérica",
                    "Nitrógeno ureico (BUN)", "Ácido úrico", "Transaminasas (TGO/TGP)",
                    "Examen general de orina", "Examen general de heces", "Prueba de embarazo",
                    "Hormona estimulante de tiroides (TSH)", "Proteína C reactiva",
                    "Prueba rápida de dengue", "Urocultivo", "Tiempo de protrombina (TP/TPT)"
            ).map(Examen::new).toList());
        }

        if (medicamentoRepository.count() == 0) {
            medicamentoRepository.saveAll(Stream.of(
                    "Acetaminofén 500 mg", "Ibuprofeno 400 mg", "Diclofenaco 50 mg", "Amoxicilina 500 mg",
                    "Azitromicina 500 mg", "Ciprofloxacino 500 mg", "Metronidazol 500 mg",
                    "Omeprazol 20 mg", "Loratadina 10 mg", "Salbutamol inhalador", "Losartán 50 mg",
                    "Enalapril 10 mg", "Metformina 850 mg", "Glibenclamida 5 mg", "Atorvastatina 20 mg",
                    "Levotiroxina 50 mcg", "Sales de rehidratación oral", "Albendazol 400 mg",
                    "Ranitidina 150 mg", "Dexametasona 4 mg"
            ).map(Medicamento::new).toList());
        }
    }
}
