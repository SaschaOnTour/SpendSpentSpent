package com.ftpix.sss.services;

import com.ftpix.sss.models.Household;
import com.ftpix.sss.models.User;
import com.ftpix.sss.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Serializes operations that depend on who is in a household, by locking the user rows of the household members
 * (or only the user's row when not in a household) until the current transaction ends.
 * <ul>
 *     <li>{@link #shared(User)}: creating or moving entries on household categories. Concurrent bookings don't block
 *     each other.</li>
 *     <li>{@link #exclusive(User)}: leaving or removing members (the separation of entries must see every booking) and
 *     changing categories (the icon check must see every category).</li>
 *     <li>{@link #joining(User, Household)}: joining a household, locks the members on both sides.</li>
 * </ul>
 * Every membership change takes one of the exclusive locks, so the members can't change while a lock is held.
 * Locks must be taken before reading the data they protect.
 */
@Service
public class HouseholdLocks {
    private final UserRepository userRepository;

    @Autowired
    public HouseholdLocks(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void shared(User user) {
        lockUntilStable(() -> userRepository.lockHouseholdUsersShared(user));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void exclusive(User user) {
        lockUntilStable(() -> userRepository.lockHouseholdUsersExclusive(user));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void joining(User user, Household household) {
        lockUntilStable(() -> userRepository.lockJoiningUsersExclusive(user, household));
    }

    /**
     * The members are read before waiting for a lock. If someone joined while waiting, they are missing, so the
     * query is repeated until it returns the same users. Once all of them are locked the members can't change.
     */
    private void lockUntilStable(Supplier<List<User>> lock) {
        List<UUID> locked = ids(lock.get());
        List<UUID> again;
        while (!(again = ids(lock.get())).equals(locked)) {
            locked = again;
        }
    }

    private static List<UUID> ids(List<User> users) {
        return users.stream().map(User::getId).toList();
    }
}
