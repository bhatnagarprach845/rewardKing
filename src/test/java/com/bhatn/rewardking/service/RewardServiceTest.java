package com.bhatn.rewardking.service;

import com.bhatn.rewardking.controller.RewardController.RedeemPointsRequest;
import com.bhatn.rewardking.controller.RewardController.RedeemPointsRequest.CartItemDTO;
import com.bhatn.rewardking.entity.StoreItem;
import com.bhatn.rewardking.entity.UserWallet;
import com.bhatn.rewardking.repository.RewardTransactionRepository;
import com.bhatn.rewardking.repository.StoreItemRepository;
import com.bhatn.rewardking.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RewardServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private RewardTransactionRepository transactionRepository;
    @Mock
    private StoreItemRepository storeItemRepository;

    private RewardService rewardService;

    @BeforeEach
    void setUp() {
        rewardService = new RewardService(walletRepository, transactionRepository, storeItemRepository);
    }

    private CartItemDTO cartItem(String itemId, int quantity) {
        CartItemDTO item = new CartItemDTO();
        item.setItemId(itemId);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void redemptionLocksWalletAndStockRowsInsteadOfPlainReads() {
        UserWallet wallet = UserWallet.builder().userId("user-1").availablePoints(100).build();
        StoreItem item = StoreItem.builder().itemId("item-1").name("Mug").pointsCost(50).stockLevel(5).build();

        when(walletRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.of(wallet));
        when(storeItemRepository.findByIdForUpdate("item-1")).thenReturn(Optional.of(item));

        rewardService.processBulkCartRedemption("user-1", List.of(cartItem("item-1", 1)));

        // The checkout path must use the row-locking repository methods, not the
        // plain unlocked reads — otherwise concurrent checkouts can double-spend
        // points or oversell stock.
        verify(walletRepository).findByUserIdForUpdate("user-1");
        verify(storeItemRepository).findByIdForUpdate("item-1");
        verify(walletRepository, never()).findByUserId(any());
        verify(storeItemRepository, never()).findById(any());
    }

    @Test
    void redemptionDeductsWalletPointsAndStock() {
        UserWallet wallet = UserWallet.builder().userId("user-1").availablePoints(100).build();
        StoreItem item = StoreItem.builder().itemId("item-1").name("Mug").pointsCost(30).stockLevel(5).build();

        when(walletRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.of(wallet));
        when(storeItemRepository.findByIdForUpdate("item-1")).thenReturn(Optional.of(item));

        rewardService.processBulkCartRedemption("user-1", List.of(cartItem("item-1", 2)));

        assertThat(wallet.getAvailablePoints()).isEqualTo(40); // 100 - (30 * 2)
        assertThat(item.getStockLevel()).isEqualTo(3); // 5 - 2
        verify(walletRepository).save(wallet);
        verify(storeItemRepository).save(item);
        verify(transactionRepository).save(any());
    }

    @Test
    void redemptionRejectsInsufficientPoints() {
        UserWallet wallet = UserWallet.builder().userId("user-1").availablePoints(10).build();
        StoreItem item = StoreItem.builder().itemId("item-1").name("Mug").pointsCost(50).stockLevel(5).build();

        when(walletRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.of(wallet));
        when(storeItemRepository.findByIdForUpdate("item-1")).thenReturn(Optional.of(item));

        assertThatThrownBy(() ->
                rewardService.processBulkCartRedemption("user-1", List.of(cartItem("item-1", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient points balance");

        verify(walletRepository, never()).save(any());
        verify(storeItemRepository, never()).save(any());
    }

    @Test
    void redemptionRejectsInsufficientStock() {
        UserWallet wallet = UserWallet.builder().userId("user-1").availablePoints(1000).build();
        StoreItem item = StoreItem.builder().itemId("item-1").name("Mug").pointsCost(50).stockLevel(1).build();

        when(walletRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.of(wallet));
        when(storeItemRepository.findByIdForUpdate("item-1")).thenReturn(Optional.of(item));

        assertThatThrownBy(() ->
                rewardService.processBulkCartRedemption("user-1", List.of(cartItem("item-1", 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of stock");

        verify(walletRepository, never()).save(any());
    }
}
