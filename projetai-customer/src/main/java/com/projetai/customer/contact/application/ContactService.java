package com.projetai.customer.contact.application;

import com.projetai.core.domain.user.support.Support;
import com.projetai.core.infra.notification.NotificationRepository;
import com.projetai.core.infra.user.support.SupportEntity;
import com.projetai.core.infra.user.support.SupportRepository;
import com.projetai.customer.contact.application.dto.ClientDto;
import com.projetai.customer.contact.application.dto.ContactAnalysisDto;
import com.projetai.customer.contact.application.dto.ContactDto;
import com.projetai.customer.contact.domain.client.Client;
import com.projetai.customer.contact.domain.contact.Contact;
import com.projetai.customer.contact.domain.contact.analysis.ContactAnalysis;
import com.projetai.customer.contact.infra.contact.ContactEntity;
import com.projetai.customer.contact.infra.contact.ContactRepository;
import com.projetai.customer.contact.infra.contact.analysis.ContactAnalysisEntity;
import com.projetai.customer.contact.infra.contact.analysis.ContactAnalysisRepository;
import com.projetai.customer.contact.infra.user.client.ClientEntity;
import com.projetai.customer.contact.infra.user.client.ClientRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final SupportRepository supportRepository;
    private final ClientRepository clientRepository;
    private final ContactAnalysisRepository contactAnalysisRepository;
    private final NotificationRepository<SupportEntity> supportNotificationRepository;
    private final NotificationRepository<ClientEntity> clientNotificationRepository;

    @Autowired
    public ContactService(ContactRepository contactRepository, SupportRepository supportRepository,
                          ClientRepository clientRepository, ContactAnalysisRepository contactAnalysisRepository,
                          NotificationRepository<SupportEntity> notificationRepository,
                          NotificationRepository<ClientEntity> clientNotificationRepository) {
        this.contactRepository = contactRepository;
        this.supportRepository = supportRepository;
        this.clientRepository = clientRepository;
        this.contactAnalysisRepository = contactAnalysisRepository;
        this.supportNotificationRepository = notificationRepository;
        this.clientNotificationRepository = clientNotificationRepository;
    }

    @Transactional
    public void makeContact(ContactDto contactDto) {
        Client client = getClientForContact(contactDto.clientDto());
        Support support = getSupportForContact();
        Contact contact = new Contact(contactDto, client, support);

        contactRepository.save(contact.makeContact());
        supportNotificationRepository.save(contact.makeNotificationToSupport());
    }

    private Support getSupportForContact() {
        return supportRepository.findFirstByAvailableTrue()
                .map(Support::dbEntityToSupport)
                .orElseThrow(() -> new RuntimeException("No support available"));
    }

    private Client getClientForContact(ClientDto clientDto) {
        if (clientDto.id() == null) {
            return new Client(clientDto.name(), clientDto.email());
        }

        return clientRepository.findById(clientDto.id())
                .map(ClientEntity::toClient)
                .orElseThrow(() -> new RuntimeException("Client not found"));
    }

    public List<ClientDto> findAllClients() {
        return clientRepository.findAll()
                .stream()
                .map(Client::dbEntityToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ClientDto createClient(ClientDto clientDto) {
        ClientEntity client = new ClientEntity(clientDto);
        client.setId(null);

        return Client.dbEntityToDto(clientRepository.save(client));
    }

    public ContactDto findContact(Long id) {
        ContactEntity contactEntity = safeFindContact(id);
        return new ContactDto(contactEntity);
    }

    public List<ContactDto> findAll() {
        List<ContactEntity> contacts = contactRepository.findAll();

        return contacts.stream()
                .map(ContactDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public void replyProblem(ContactAnalysisDto contactAnalysis) {
        if (contactAnalysis.isReplied()) {
            createTicketAnalysis(contactAnalysis);
        }

        closeContact(contactAnalysis.contactId());
    }

    private void createTicketAnalysis(ContactAnalysisDto contactAnalysis) {
        ContactEntity contactEntity = safeFindContact(contactAnalysis.contactId());

        Contact contact = new Contact(contactEntity);
        contactRepository.save(contact.replyProblem());

        ContactAnalysis analysis = new ContactAnalysis(contactAnalysis);
        contactAnalysisRepository.save(new ContactAnalysisEntity(analysis));
    }

    private ContactEntity safeFindContact(Long id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact not found"));
    }

    private void closeContact(Long contactId) {
        ContactEntity contactEntity = safeFindContact(contactId);

        Contact contact = new Contact(contactEntity);
        contactRepository.save(contact.closeContact());
        clientNotificationRepository.save(contact.makeNotificationToClient());
    }
}
