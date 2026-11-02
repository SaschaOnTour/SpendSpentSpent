package com.ftpix.sss.services;

import com.ftpix.sss.TestConfig;
import com.ftpix.sss.TestContainerTest;
import com.ftpix.sss.models.Category;
import com.ftpix.sss.models.Household;
import com.ftpix.sss.models.User;
import com.ftpix.sss.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * A bulk category update of one member racing with an icon change of another member. The bulk update is paused when
 * it asks for the household lock, which must happen before it reads or writes any category.
 */
@Import(TestConfig.class)
public class HouseholdCategoryLockingTest extends TestContainerTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private HouseholdService householdService;
    @Autowired
    private CategoryService categoryService;
    @MockitoSpyBean
    private HouseholdLocks householdLocks;

    private User partner;
    private Category house;
    private Category cloud;
    private final CountDownLatch bulkWantsLock = new CountDownLatch(1);
    private final CountDownLatch bulkMayLock = new CountDownLatch(1);

    @BeforeEach
    public void setUpHousehold() throws Exception {
        partner = new User();
        partner.setEmail("partner" + System.nanoTime() + "@test.com");
        partner.setFirstName("partner");
        partner.setLastName("test");
        partner = userRepository.save(partner);

        Household household = householdService.createHousehold(currentUser);
        householdService.inviteUser(currentUser, partner.getEmail());
        householdService.acceptInvitation(partner, household);

        house = icon("house");
        cloud = icon("cloud");

        // pauses the partner (doing the bulk update) until the other member's change went through
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId().equals(partner.getId())) {
                bulkWantsLock.countDown();
                await(bulkMayLock);
            }
            return invocation.callRealMethod();
        }).when(AopTestUtils.<HouseholdLocks>getUltimateTargetObject(householdLocks)).exclusive(any(User.class));
    }

    private Category icon(String icon) throws Exception {
        return categoryService.getAll(currentUser).stream().filter(c -> c.getIcon().equals(icon)).findFirst().orElseThrow();
    }

    private void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }

    private Category edit(Category category, String icon, int order) {
        Category edit = new Category();
        edit.setId(category.getId());
        edit.setIcon(icon);
        edit.setCategoryOrder(order);
        return edit;
    }

    private long count(String icon) throws Exception {
        return categoryService.getAll(currentUser).stream().filter(c -> c.getIcon().equals(icon)).count();
    }

    @Test
    public void bulkUpdateAndIconChangeDontDeadlock() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<List<Category>> bulk = executor.submit(() -> categoryService.updateMany(List.of(edit(house, "house", 42), edit(cloud, "gas", cloud.getCategoryOrder())), partner));
            await(bulkWantsLock);

            Category diff = new Category();
            diff.setIcon("food");
            assertTrue(categoryService.mergeCategory(house.getId(), diff, currentUser));
            bulkMayLock.countDown();

            assertNotNull(bulk.get(20, TimeUnit.SECONDS));
            assertEquals(1, count("gas"));
            assertEquals(42, categoryService.get(house.getId(), currentUser).getCategoryOrder());
        } finally {
            bulkMayLock.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void bulkUpdateCannotDuplicateAnIcon() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<List<Category>> bulk = executor.submit(() -> categoryService.updateMany(List.of(edit(house, "house", house.getCategoryOrder()), edit(cloud, "food", cloud.getCategoryOrder())), partner));
            await(bulkWantsLock);

            Category diff = new Category();
            diff.setIcon("food");
            assertTrue(categoryService.mergeCategory(house.getId(), diff, currentUser));
            bulkMayLock.countDown();

            bulk.get(20, TimeUnit.SECONDS);
            assertEquals(1, count("food"));
        } finally {
            bulkMayLock.countDown();
            executor.shutdownNow();
        }
    }
}
