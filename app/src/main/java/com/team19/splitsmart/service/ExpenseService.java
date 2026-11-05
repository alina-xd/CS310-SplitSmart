package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Category;
import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Group;
import com.team19.splitsmart.data.model.SplitMethod;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.repository.GroupRepository;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.service.split.SplitStrategyFactory;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.DateUtil;

import java.util.Map;

/** C7 Expense Service. Owners: Part B (create/edit/delete), Part C (previewShares). */
public class ExpenseService {

    private final TransactionRepository txRepo;
    private final GroupRepository groupRepo;
    private final SettlementService settlementService;

    public ExpenseService() {
        this(new TransactionRepository(), new GroupRepository(), new SettlementService());
    }

    public ExpenseService(TransactionRepository txRepo, SettlementService settlementService) {
        this(txRepo, new GroupRepository(), settlementService);
    }

    public ExpenseService(TransactionRepository txRepo, GroupRepository groupRepo,
                          SettlementService settlementService) {
        this.txRepo = txRepo;
        this.groupRepo = groupRepo;
        this.settlementService = settlementService;
    }

    /**
     * Shares in cents for each participant. inputs is keyed by participant uid (use a
     * LinkedHashMap to keep order); values are ignored for EQUAL, dollars for EXACT_AMOUNT and
     * percentages for PERCENTAGE. Pure calculation, no Firebase call.
     */
    public Map<String, Long> previewShares(long totalCents, SplitMethod method, Map<String, Double> inputs) {
        // Part C implements the strategies; this delegation is done, so Part B never edits it.
        return new SplitStrategyFactory().getStrategy(method).computeShares(totalCents, inputs);
    }

    /** Validates, then saves with the next sequenceNo and today's date (FR-2.1). */
    public void createExpense(Expense expense, Callback<Void> callback) {
        if (expense.getId() != null) {
            callback.onFailure(new ValidationException("permission", "Use Edit to change an existing expense"));
            return;
        }
        withGroup(expense.getGroupId(), callback, group -> {
            try {
                validate(expense, group);
            } catch (ValidationException e) {
                callback.onFailure(e);
                return;
            }
            txRepo.nextSequenceNo(expense.getGroupId(), new Callback<Long>() {
                @Override
                public void onSuccess(Long sequenceNo) {
                    expense.setSequenceNo(sequenceNo);
                    expense.setCreatedDate(DateUtil.today());
                    txRepo.save(expense, new Callback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction saved) {
                            callback.onSuccess(null);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            callback.onFailure(e);
                        }
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    callback.onFailure(e);
                }
            });
        });
    }

    /** Creator only, not locked. Keeps the original createdDate, sequenceNo and creatorId. */
    public void updateExpense(Expense expense, String editorId, Callback<Void> callback) {
        loadEditable(expense, editorId, "edit", callback, saved -> {
            expense.setCreatedDate(saved.getCreatedDate());
            expense.setSequenceNo(saved.getSequenceNo());
            expense.setCreatorId(saved.getCreatorId());
            withGroup(saved.getGroupId(), callback, group -> {
                try {
                    validate(expense, group);
                } catch (ValidationException e) {
                    callback.onFailure(e);
                    return;
                }
                txRepo.update(expense, callback);
            });
        });
    }

    /** Creator only, not locked. The confirmation dialog is on the detail screen (Part E). */
    public void deleteExpense(Expense expense, String editorId, Callback<Void> callback) {
        loadEditable(expense, editorId, "delete", callback, saved -> txRepo.delete(saved, callback));
    }

    private void validate(Expense expense, Group group) throws ValidationException {
        if (expense.getTitle() == null || expense.getTitle().trim().isEmpty()) {
            throw new ValidationException("title", "Enter a title");
        }
        if (expense.getAmountCents() <= 0) {
            throw new ValidationException("amount", "The amount must be more than $0.00");
        }
        if (!group.hasMember(expense.getCreatorId())) {
            throw new ValidationException("permission", "The creator must be a member of this group");
        }
        String payerId = expense.getPayerId();
        if (payerId == null || payerId.isEmpty()) {
            throw new ValidationException("payer", "Choose who paid");
        }
        if (!group.hasMember(payerId)) {
            throw new ValidationException("payer", "The payer must be a member of this group");
        }
        Map<String, Long> shares = expense.getShares();
        if (shares == null || shares.size() < 2) {
            throw new ValidationException("participants", "Choose at least 2 participants");
        }
        for (String participantId : shares.keySet()) {
            if (!group.hasMember(participantId)) {
                throw new ValidationException("participants", "Every participant must be a member of this group");
            }
        }
        if (!shares.containsKey(payerId)) {
            throw new ValidationException("participants", "The payer must be one of the participants");
        }
        long total = 0;
        for (Long share : shares.values()) {
            if (share == null || share < 0 || share > expense.getAmountCents() - total) {
                throw new ValidationException("split", "Shares must be nonnegative and add up to the expense");
            }
            total += share;
        }
        if (total != expense.getAmountCents()) {
            throw new ValidationException("split", "The shares must add up to the full amount");
        }
        if (expense.getCategory() == null) {
            expense.setCategory(Category.OTHER);
        }
    }

    /** Loads the saved expense, then checks the creator and lock rules against the saved version. */
    private void loadEditable(Expense expense, String editorId, String action, Callback<Void> callback,
                              Step<Expense> next) {
        txRepo.findById(expense.getGroupId(), expense.getId(), new Callback<Transaction>() {
            @Override
            public void onSuccess(Transaction transaction) {
                if (!(transaction instanceof Expense)) {
                    callback.onFailure(new IllegalStateException("This expense no longer exists"));
                    return;
                }
                Expense saved = (Expense) transaction;
                if (editorId == null || !editorId.equals(saved.getCreatorId())) {
                    callback.onFailure(new ValidationException("permission",
                            "Only the creator can " + action + " this expense"));
                    return;
                }
                settlementService.isLocked(saved, new Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean locked) {
                        if (!Boolean.FALSE.equals(locked)) {
                            callback.onFailure(new ValidationException("permission",
                                    "This expense is locked by a later settlement"));
                            return;
                        }
                        next.run(saved);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        callback.onFailure(e);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    private void withGroup(String groupId, Callback<Void> callback, Step<Group> next) {
        groupRepo.findById(groupId, new Callback<Group>() {
            @Override
            public void onSuccess(Group group) {
                if (group == null) {
                    callback.onFailure(new IllegalStateException("This group no longer exists"));
                    return;
                }
                next.run(group);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    private interface Step<T> {
        void run(T value);
    }
}
