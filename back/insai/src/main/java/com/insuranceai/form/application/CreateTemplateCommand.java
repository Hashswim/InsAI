package com.insuranceai.form.application;

import com.insuranceai.form.domain.FormField;

import java.util.List;

public record CreateTemplateCommand(String code, String name, List<FormField> fields) {
}
