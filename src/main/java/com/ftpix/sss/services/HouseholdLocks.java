package com.ftpix.sss.services;

import com.ftpix.sss.models.User;
import com.ftpix.sss.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializes operations that depend on who is in a household, by locking the user rows of the household members
 * (or only the user's row when not in a household) until the current transaction ends.
 * <ul>
 *     <li>{@link #shared(User)}: creating or moving entries on household categories. Concurrent bookings don't block
 *     each other.</li>
 *     <li>{@link #exclusive(User)}: changing the membership (the separation of entries must see every booking) or
 *     giving a category an icon (the uniqueness check must see every category).</li>
 * </ul>
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
        userRepository.lockHouseholdUsersShared(user);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void exclusive(User user) {
        userRepository.lockHouseholdUsersExclusive(user);
    }
}
