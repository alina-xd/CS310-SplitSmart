package com.team19.splitsmart.service;

import com.team19.splitsmart.data.model.Expense;
import com.team19.splitsmart.data.model.Settlement;
import com.team19.splitsmart.data.model.Transaction;
import com.team19.splitsmart.data.repository.GroupRepository;
import com.team19.splitsmart.data.repository.TransactionRepository;
import com.team19.splitsmart.util.Callback;
import com.team19.splitsmart.util.DateUtil;
import com.team19.splitsmart.util.MoneyUtil;

import java.util.Objects;
import java.util.function.Consumer;

/** Validates recorded repayments and derives expense locks from transaction order. */
public class SettlementService {
    private final TransactionRepository txRepo;
    private final BalanceService balanceService;
    private final GroupRepository groupRepo;

    public SettlementService() {
        this(new TransactionRepository(), new BalanceService());
    }

    public SettlementService(TransactionRepository txRepo, BalanceService balanceService) {
        this(txRepo, balanceService, new GroupRepository());
    }

    public SettlementService(
            TransactionRepository txRepo,
            BalanceService balanceService,
            GroupRepository groupRepo) {
        this.txRepo = txRepo;
        this.balanceService = balanceService;
        this.groupRepo = groupRepo;
    }

    public void createSettlement(Settlement settlement, Callback<Void> callback) {
        if (settlement.getId() != null) {
            callback.onFailure(
                    new ValidationException(
                            "permission", "Use Edit to change an existing settlement"));
            return;
        }
        validate(
                settlement,
                callback,
                () ->
                        getMaxAllowed(
                                settlement.getGroupId(),
                                settlement.getPayerId(),
                                settlement.getRecipientId(),
                                then(
                                        callback,
                                        maximum -> {
                                            if (!checkAmount(settlement, maximum, callback)) return;
                                            txRepo.nextSequenceNo(
                                                    settlement.getGroupId(),
                                                    then(
                                                            callback,
                                                            sequence -> {
                                                                settlement.setSequenceNo(sequence);
                                                                settlement.setCreatedDate(
                                                                        DateUtil.today());
                                                                txRepo.save(
                                                                        settlement,
                                                                        then(
                                                                                callback,
                                                                                saved ->
                                                                                        callback
                                                                                                .onSuccess(
                                                                                                        null)));
                                                            }));
                                        })));
    }

    /** UC-4: edit amount or note; parties, creator, date, and ordering remain unchanged. */
    public void updateSettlement(Settlement settlement, String editorId, Callback<Void> callback) {
        loadOwned(
                settlement,
                editorId,
                callback,
                saved -> {
                    if (!Objects.equals(saved.getPayerId(), settlement.getPayerId())
                            || !Objects.equals(
                                    saved.getRecipientId(), settlement.getRecipientId())) {
                        callback.onFailure(
                                new ValidationException(
                                        "member", "Only the amount and note can be edited"));
                        return;
                    }
                    settlement.setCreatorId(saved.getCreatorId());
                    settlement.setCreatedDate(saved.getCreatedDate());
                    settlement.setSequenceNo(saved.getSequenceNo());
                    validate(
                            settlement,
                            callback,
                            () ->
                                    balanceService.getPairBalance(
                                            saved.getGroupId(),
                                            saved.getPayerId(),
                                            saved.getRecipientId(),
                                            then(
                                                    callback,
                                                    pair -> {
                                                        // Undo this record BEFORE clamping. Later
                                                        // expenses can reverse the pair's debt.
                                                        long maximum;
                                                        try {
                                                            maximum =
                                                                    Math.max(
                                                                            0L,
                                                                            Math.subtractExact(
                                                                                    saved
                                                                                            .getAmountCents(),
                                                                                    pair));
                                                        } catch (ArithmeticException e) {
                                                            callback.onFailure(
                                                                    new ValidationException(
                                                                            "amount",
                                                                            "The combined balance"
                                                                                + " is too large"));
                                                            return;
                                                        }
                                                        if (checkAmount(
                                                                settlement, maximum, callback))
                                                            txRepo.update(settlement, callback);
                                                    })));
                });
    }

    public void deleteSettlement(Settlement settlement, String editorId, Callback<Void> callback) {
        loadOwned(settlement, editorId, callback, saved -> txRepo.delete(saved, callback));
    }

    public void getMaxAllowed(
            String groupId, String payerId, String recipientId, Callback<Long> callback) {
        balanceService.getPairBalance(
                groupId,
                payerId,
                recipientId,
                then(callback, pair -> callback.onSuccess(pair < 0 ? -pair : 0L)));
    }

    /** A later settlement locks a positive share between its parties, in either direction. */
    public void isLocked(Expense expense, Callback<Boolean> callback) {
        txRepo.findByGroup(
                expense.getGroupId(),
                then(
                        callback,
                        transactions -> {
                            for (Transaction t : transactions) {
                                if (!(t instanceof Settlement)
                                        || !expense.getGroupId().equals(t.getGroupId())
                                        || t.getSequenceNo() <= expense.getSequenceNo()) continue;
                                Settlement settlement = (Settlement) t;
                                String other = null;
                                if (Objects.equals(expense.getPayerId(), settlement.getPayerId())) {
                                    other = settlement.getRecipientId();
                                } else if (Objects.equals(
                                        expense.getPayerId(), settlement.getRecipientId())) {
                                    other = settlement.getPayerId();
                                }
                                if (other != null
                                        && !other.equals(expense.getPayerId())
                                        && expense.getShare(other) > 0) {
                                    callback.onSuccess(true);
                                    return;
                                }
                            }
                            callback.onSuccess(false);
                        }));
    }

    private void validate(Settlement settlement, Callback<?> callback, Runnable next) {
        if (settlement.getAmountCents() <= 0) {
            callback.onFailure(
                    new ValidationException("amount", "The amount must be more than $0.00"));
            return;
        }
        if (settlement.getPayerId() == null
                || settlement.getRecipientId() == null
                || settlement.getPayerId().equals(settlement.getRecipientId())) {
            callback.onFailure(
                    new ValidationException("member", "Choose two different group members"));
            return;
        }
        if (!Objects.equals(settlement.getCreatorId(), settlement.getPayerId())
                && !Objects.equals(settlement.getCreatorId(), settlement.getRecipientId())) {
            callback.onFailure(
                    new ValidationException(
                            "permission", "Only the payer or recipient can record a settlement"));
            return;
        }
        groupRepo.findById(
                settlement.getGroupId(),
                then(
                        callback,
                        group -> {
                            if (group == null) {
                                callback.onFailure(
                                        new IllegalStateException("This group no longer exists"));
                            } else if (!group.hasMember(settlement.getPayerId())
                                    || !group.hasMember(settlement.getRecipientId())) {
                                callback.onFailure(
                                        new ValidationException(
                                                "member",
                                                "Both people must be members of this group"));
                            } else next.run();
                        }));
    }

    private boolean checkAmount(Settlement settlement, long maximum, Callback<?> callback) {
        if (settlement.getAmountCents() <= maximum) return true;
        callback.onFailure(
                new ValidationException("amount", "They only owe " + MoneyUtil.format(maximum)));
        return false;
    }

    private void loadOwned(
            Settlement settlement,
            String editorId,
            Callback<?> callback,
            Consumer<Settlement> next) {
        txRepo.findById(
                settlement.getGroupId(),
                settlement.getId(),
                then(
                        callback,
                        transaction -> {
                            if (!(transaction instanceof Settlement)) {
                                callback.onFailure(
                                        new IllegalStateException(
                                                "This settlement no longer exists"));
                            } else if (editorId == null
                                    || !editorId.equals(transaction.getCreatorId())) {
                                callback.onFailure(
                                        new ValidationException(
                                                "permission",
                                                "Only the creator can change this settlement"));
                            } else next.accept((Settlement) transaction);
                        }));
    }

    private static <T> Callback<T> then(Callback<?> outer, Consumer<T> success) {
        return new Callback<T>() {
            @Override
            public void onSuccess(T value) {
                success.accept(value);
            }

            @Override
            public void onFailure(Exception e) {
                outer.onFailure(e);
            }
        };
    }
}
