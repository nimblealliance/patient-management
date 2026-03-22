package com.pm.patientservice.service;

import com.pm.patientservice.dto.PagedPatientResponseDTO;
import com.pm.patientservice.dto.PatientRequestDTO;
import com.pm.patientservice.dto.PatientResponseDTO;
import com.pm.patientservice.exception.EmailAlreadyExistsException;
import com.pm.patientservice.exception.PatientNotFoundException;
import com.pm.patientservice.grpc.BillingServiceGrpcClient;
import com.pm.patientservice.kafka.KafkaProducer;
import com.pm.patientservice.mapper.PatientMapper;
import com.pm.patientservice.model.Patient;
import com.pm.patientservice.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patientRepository;
    private final KafkaProducer kafkaProducer;
    private final BillingServiceGrpcClient billingServiceGrpcClient;

    @Autowired
    public PatientService(PatientRepository patientRepository, KafkaProducer kafkaProducer, BillingServiceGrpcClient billingServiceGrpcClient) {
        this.patientRepository = patientRepository;
        this.kafkaProducer = kafkaProducer;
        this.billingServiceGrpcClient=billingServiceGrpcClient;
    }


    @Cacheable(
            value = "patients",
            key = "#page + '-' + #size + '-' + #sort + '-' + #sortField"
    )
    public PagedPatientResponseDTO getPatients(int page , int size , String sort , String sortField , String searchValue){

        
        try { //just to simulate slow DB reads when cache miss happens
            Thread.sleep(2000);
        }catch (InterruptedException e){
            log.info(e.getMessage());

        }
        //has all the info about pages and stuff being requested.
        Pageable pageable = PageRequest.of(page -1, //Spring's Pageable system is zero based indexing hence the -1,
                size,
                sort.equalsIgnoreCase("desc") ? Sort.by(sortField).descending() : Sort.by(sortField).ascending());

        //variable to hold paged responses from JPA
        Page<Patient> patientPage;

        if(searchValue == null || searchValue.isBlank()){
            patientPage = patientRepository.findAll(pageable);
        }else {
            patientPage = patientRepository.findByNameContainingIgnoreCase(searchValue,pageable);
        }

        //convert all Patients present in patientPage object to DTOs so that we can add it to PagedPatientResponseDTO
        List<PatientResponseDTO> patientResponseDTOs = patientPage.getContent()
                .stream()
                .map(PatientMapper::toDTO)
                .toList();

        return new PagedPatientResponseDTO(
                patientResponseDTOs,
                patientPage.getNumber()+1,
                patientPage.getSize(),
                patientPage.getTotalPages(),
                (int)patientPage.getTotalElements());

    }

    public PatientResponseDTO createPatient(PatientRequestDTO patientRequestDTO){
        if(patientRepository.existsByEmail(patientRequestDTO.getEmail())){
            throw new EmailAlreadyExistsException("A patient with this email already exists "+patientRequestDTO.getEmail());
        }
        Patient newPatient = patientRepository.save(PatientMapper.toModel(patientRequestDTO));
        billingServiceGrpcClient.createBillingAccount(newPatient.getId().toString(), newPatient.getName(), newPatient.getEmail());
        kafkaProducer.sendPatientCreatedEvent(newPatient);

        return PatientMapper.toDTO(newPatient);

    }

    public PatientResponseDTO updatePatient(UUID id , PatientRequestDTO patientRequestDTO){

        Patient patient = patientRepository.findById(id).orElseThrow(() -> new PatientNotFoundException("Patient not found with ID: "+ id));

        if(patientRepository.existsByEmailAndIdNot(patientRequestDTO.getEmail(),id)){
            throw new EmailAlreadyExistsException("A patient with this email already exists "+patientRequestDTO.getEmail());
        }

        patient.setName(patientRequestDTO.getName());
        patient.setAddress(patientRequestDTO.getAddress());
        patient.setEmail(patientRequestDTO.getEmail());
        patient.setDateOfBirth(LocalDate.parse(patientRequestDTO.getDateOfBirth()));

        Patient updatedPatient = patientRepository.save(patient);
        kafkaProducer.sendPatientUpdatedEvent(updatedPatient);

        return PatientMapper.toDTO(updatedPatient);
    }

    public void deletePatient(UUID id){
        if(!patientRepository.existsById(id)){
            throw new PatientNotFoundException("Patient not found with ID: "+id);
        }
        patientRepository.deleteById(id);
    }










}
