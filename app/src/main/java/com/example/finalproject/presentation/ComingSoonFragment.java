package com.example.finalproject.presentation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.finalproject.R;

public class ComingSoonFragment extends Fragment {
    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";

    public static ComingSoonFragment newInstance(String title, String message) {
        ComingSoonFragment fragment = new ComingSoonFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_TITLE, title);
        arguments.putString(ARG_MESSAGE, message);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_coming_soon, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        ((TextView) view.findViewById(R.id.pageTitle)).setText(
            arguments == null ? "Journify" : arguments.getString(ARG_TITLE, "Journify")
        );
        ((TextView) view.findViewById(R.id.pageMessage)).setText(
            arguments == null ? "Tính năng đang được hoàn thiện." : arguments.getString(ARG_MESSAGE, "")
        );
    }
}
