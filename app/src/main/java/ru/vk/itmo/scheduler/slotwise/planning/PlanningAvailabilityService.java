package ru.vk.itmo.scheduler.slotwise.planning;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static ru.vk.itmo.scheduler.slotwise.planning.PlanningApi.*;

@Service
public class PlanningAvailabilityService {
    public BusyResponse busy(long id, long user) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
    public BusyResponse saveBusy(long id, long user, SaveBusyRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
    public BusyResponse submit(long id, long user) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
    public CollectionResponse collection(long id, long user) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
    public CollectionResponse changeCollection(long id, long user, ChangeCollectionRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
    public CollectionResponse close(long id, long user) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
    public RulesResponse rules(long id, long user) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
    public RulesResponse saveRules(long id, long user, SaveRulesRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
